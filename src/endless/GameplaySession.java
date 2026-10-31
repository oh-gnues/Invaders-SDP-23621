package endless;

import engine.*;

import entity.*;
import item.ItemAPI;
import item.ItemSystem;
import shop.Skin;
import engine.soundEngine.Sound;
import engine.soundEngine.SoundManager;
import java.awt.Graphics2D;
import java.awt.Font;
import java.util.*;

/**
 * Connects an arena to the game's existing services. Owns no item definitions,
 * ship stats, skin animation, wallet files, achievement rules or meteor physics.
 * Endless owns only its wave pacing, upgrade draft and separate score record.
 */
public final class GameplaySession {
    private final Ship ship;
    private final Skin skin;
    private final SharedAccess.Items items;
    private final boolean persistent, audio;
    private final CoinDropManager coinDrops;
    private final List<Coin> coins = new ArrayList<Coin>();
    private final List<GameplayExtension> extensions = new ArrayList<GameplayExtension>();
    private List<Entity> currentEnemies = Collections.emptyList();
    private final Set<String> knownAchievements = new HashSet<String>();
    private final Deque<String> notifications = new ArrayDeque<String>();
    private final PointsEffect points = new PointsEffect(130);
    private MeteorManager meteors;
    private SharedAccess.Meteors meteorClock;
    private double activeMillis, frameRemainder;
    private int wave, width, height, coinsEarned, pendingDiamonds, bankedDiamonds, completedWaves;
    private boolean tookDamage, finished;
    private ShopEffects shopEffects=new ShopEffects(Collections.emptySet());

    /** Reads the same selected loadout as campaign; QA may read but never award progress. */
    public static GameplaySession open(boolean testing, Random random) {
        SharedAccess.Loadout loadout=SharedAccess.loadout(testing);
        GameplaySession session=new GameplaySession(loadout.ship,loadout.skin,loadout.equipment,!testing,true,random);
        session.shopEffects=new ShopEffects(loadout.effects);
        if(loadout.shape!=null)session.ship.setShapeSprite(loadout.shape.getSprite());
        return session;
    }

    /** Explicit loadout and no persistence/audio, for simulations and regression tests. */
    public static GameplaySession isolated(Ship.ShipType type, Skin skin,
            ItemAPI.ItemInfo equipment, Random random) {
        return new GameplaySession(type, skin, equipment, false, false, random);
    }

    private GameplaySession(Ship.ShipType type, Skin skin, ItemAPI.ItemInfo equipment,
            boolean persistent, boolean audio, Random random) {
        this.ship = new Ship(0, 0, type);
        this.skin = skin;
        this.persistent = persistent;
        this.audio = audio;
        this.items = new SharedAccess.Items(new Random(random.nextLong()),equipment,persistent);
        this.coinDrops = new CoinDropManager(CoinDropManager.DEFAULT_DROP_CHANCE, new Random(random.nextLong()));
        if (persistent)
            for (Achievement achievement : Core.getAchievementManager().getAchievements())
                if (achievement.isUnlocked()) knownAchievements.add(achievement.getId());
        for (GameplayExtension extension : ServiceLoader.load(GameplayExtension.class))
            extensions.add(extension);
    }

    public Ship getShip() { return ship; }
    public ShopEffects getShopEffects() { return shopEffects; }
    public Skin getSkin() { return skin; }
    public SharedAccess.Items getItems() { return items; }
    public boolean isPersistent() { return persistent; }
    public int getWave() { return wave; }
    public List<Entity> getEnemies() { return Collections.unmodifiableList(currentEnemies); }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getCoinsEarned() { return coinsEarned; }
    public int getPendingDiamonds() { return pendingDiamonds; }
    public int getBankedDiamonds() { return bankedDiamonds; }
    public int getWalletCoins() { return persistent ? CurrencyManager.getInstance().getCoins() : coinsEarned; }
    public int getWalletDiamonds() { return persistent ? DiamondManager.getInstance().getDiamonds() : bankedDiamonds; }

    public void start(int width, int height) {
        this.width = width; this.height = height;
        if (audio) SoundManager.getInstance().playBgm(Sound.BGM_GAME);
        emit(GameplayExtension.Event.START, null);
    }

    public void beginWave(int number, boolean boss) {
        shopEffects.beginWave();
        wave = number; tookDamage = false; currentEnemies = Collections.emptyList();
        // Repeat the campaign's non-final meteor stages instead of inventing another spawner.
        int stage = 1 + (number - 1) % Math.max(1, Core.getNumLevels() - 1);
        meteorClock=boss?null:new SharedAccess.Meteors(stage,width,height,130);
        meteors=meteorClock==null?null:meteorClock.nativeManager;
        emit(GameplayExtension.Event.WAVE_STARTED, number);
    }

    public Entity createEnemy(int number, int index, int x, int y) {
        for (GameplayExtension extension : extensions) {
            Entity enemy = extension.createEnemy(this, number, index, x, y);
            if (enemy != null) return enemy;
        }
        return CombatCatalog.enemyForWave(number,index,x,y);
    }

    public Boss createBoss(int number, int centerX, int y) {
        for (GameplayExtension extension : extensions) {
            Boss boss = extension.createBoss(this,number,centerX,y);
            if (boss != null) return boss;
        }
        return CombatCatalog.bossForStage(number,centerX,y);
    }

    public double enemyHealth(Entity enemy, double normalHealth) {
        double health=normalHealth;
        for (GameplayExtension extension : extensions) health=extension.enemyHealth(this,enemy,health);
        if (!Double.isFinite(health) || health<=0) throw new IllegalArgumentException("Invalid enemy health from gameplay extension");
        return health;
    }

    /** Called only during active play; timers and native entities freeze in all overlays. */
    public void update(double seconds, Collection<? extends Entity> enemies, boolean canCatch) {
        if (finished) return;
        currentEnemies = new ArrayList<Entity>(enemies);
        activeMillis += seconds * 1000;
        frameRemainder += seconds * 60;
        while (frameRemainder >= 1) {
            frameRemainder--;
            Iterator<Coin> iterator = coins.iterator();
            while (iterator.hasNext()) {
                Coin coin = iterator.next(); coin.update();
                if (canCatch && overlaps(coin, ship)) {
                    awardCoins(coin.getValue()); iterator.remove();
                } else if (coin.getPositionY() > height) iterator.remove();
            }
        }
        if (meteors != null) {
            meteorClock.sync(seconds);
            List<EnemyShip> ordinary = new ArrayList<EnemyShip>();
            for (Entity enemy : enemies) if (enemy instanceof EnemyShip) ordinary.add((EnemyShip) enemy);
            meteors.trySpawn(ordinary, ship);
            int caught = meteors.update(ship, canCatch);
            pendingDiamonds += caught;
            if (caught > 0) sound(Sound.DIAMOND_PICKUP);
        }
        collectAchievements();
        for (GameplayExtension extension : extensions) extension.update(this, seconds);
    }

    public boolean interceptPlayerBullet(Bullet bullet) {
        return meteors != null && meteors.tryHit(bullet);
    }

    public void shot(int count) { sound(Sound.SHOOT); emit(GameplayExtension.Event.SHOT, count); }
    public void enemyHit(Entity enemy) { emit(GameplayExtension.Event.ENEMY_HIT, enemy); }

    public void enemyDefeated(Entity enemy, boolean boss, int score) {
        if (enemy instanceof Boss) ((Boss) enemy).destroy();
        else if (enemy instanceof EnemyShip) ((EnemyShip) enemy).destroy();
        shopEffects.enemyDefeated(enemy,currentEnemies);
        if (boss || coinDrops.rollForDrop())
            coins.add(new Coin(enemy.getPositionX() + enemy.getWidth() / 2,
                    enemy.getPositionY() + enemy.getHeight() / 2,
                    SharedAccess.coinValue(boss)));
        points.spawn(enemy.getPositionX() + enemy.getWidth() / 2,
                enemy.getPositionY() + enemy.getHeight() / 2, score, (long) activeMillis);
        if (persistent) {
            Core.getAchievementManager().recordEnemyDefeated();
            if (boss) Core.getAchievementManager().recordBossDefeated();
        }
        sound(Sound.ENEMY_EXPLOSION);
        collectAchievements();
        emit(GameplayExtension.Event.ENEMY_DEFEATED, enemy);
    }

    public void playerHit(int lives) {
        shopEffects.playerHit(ship,lives);
        tookDamage = true;
        GameEvents.emit(GameEvents.Type.PLAYER_HIT, lives);
        sound(Sound.PLAYER_EXPLOSION);
        emit(GameplayExtension.Event.PLAYER_HIT, lives);
    }

    public void itemEvent(ItemAPI.ItemEvent event) {
        if (event.type == ItemAPI.EventType.ITEM_COLLECTED) sound(Sound.MENU_SELECT);
        collectAchievements();
        emit(GameplayExtension.Event.ITEM, event);
    }

    public void waveCleared() {
        for (Coin coin : coins) awardCoins(coin.getValue());
        coins.clear();
        if (meteors != null) pendingDiamonds += meteors.collectRemaining();
        // Same risk rule as campaign: wave N awards N pending diamonds.
        pendingDiamonds = (int) Math.min(Integer.MAX_VALUE, (long) pendingDiamonds + wave);
        completedWaves++;
        if (persistent) {
            Core.getAchievementManager().recordFlawlessLevelCompleted(tookDamage);
            Core.getAchievementManager().recordRunCoins(coinsEarned);
        }
        collectAchievements();
        sound(Sound.LEVEL_UP);
        emit(GameplayExtension.Event.WAVE_CLEARED, wave);
    }

    /** Exactly once. Voluntary extraction banks diamonds; death forfeits the pending pot. */
    public void finish(boolean cashOut, int bullets, int kills) {
        if (finished) return;
        finished = true;
        if (cashOut && pendingDiamonds > 0) {
            bankedDiamonds = pendingDiamonds;
            if (persistent) DiamondManager.getInstance().addDiamonds(pendingDiamonds);
        }
        pendingDiamonds = 0;
        if (persistent) {
            Core.getAchievementManager().recordAccuracy(bullets, kills, completedWaves);
            Core.getAchievementManager().recordRunCoins(coinsEarned);
        }
        collectAchievements();
        items.endLevel(); coins.clear(); if (meteors != null) meteors.clear();
        sound(Sound.GAME_OVER);
        if (audio) SoundManager.getInstance().stopBgm();
        emit(GameplayExtension.Event.FINISH, cashOut);
        shopEffects.clear();
    }

    public void resumeAudio() { if (audio) SoundManager.getInstance().playBgm(Sound.BGM_GAME); }
    public void leave() { if (audio) SoundManager.getInstance().playBgm(Sound.BGM_TITLE); }

    private void awardCoins(int amount) {
        coinsEarned += amount;
        if (persistent) CurrencyManager.getInstance().addCoins(amount);
        sound(Sound.COIN_PICKUP);
    }

    private void collectAchievements() {
        if (!persistent) return;
        for (Achievement achievement : Core.getAchievementManager().getAchievements())
            if (achievement.isUnlocked() && knownAchievements.add(achievement.getId())) {
                notifications.add(achievement.getName()); sound(Sound.ACHIEVEMENT);
            }
    }
    public String pollAchievement() { return notifications.poll(); }

    public void drawWorld(DrawManager drawing, Graphics2D graphics, Font font) {
        for (Coin coin : coins) drawing.drawCoin(coin, coin.getPositionX(), coin.getPositionY());
        if (meteors != null) {meteorClock.sync(0);meteors.draw(drawing);}
        points.draw(graphics, width, font, (long) activeMillis);
        for (GameplayExtension extension : extensions) {
            Graphics2D copy = (Graphics2D) graphics.create();
            try { extension.draw(this, copy); } finally { copy.dispose(); }
        }
    }

    public void emit(GameplayExtension.Event event, Object value) {
        for (GameplayExtension extension : extensions) extension.onEvent(this, event, value);
    }
    private void sound(Sound sound) { if (audio) SoundManager.getInstance().playSfx(sound); }
    private static boolean overlaps(Entity a, Entity b) {
        return a.getPositionX() < b.getPositionX() + b.getWidth()
            && a.getPositionX() + a.getWidth() > b.getPositionX()
            && a.getPositionY() < b.getPositionY() + b.getHeight()
            && a.getPositionY() + a.getHeight() > b.getPositionY();
    }
}
