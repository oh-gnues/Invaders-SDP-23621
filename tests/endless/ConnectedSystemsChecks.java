package engine;

import endless.*;

import entity.*;
import item.ItemAPI.*;
import item.ItemSystem;
import shop.Skin;
import screen.EndlessMode.Run;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Exercises real shared services. Run only from an isolated connected-check/classes directory. */
public final class ConnectedSystemsChecks {
    private static int checks;
    public static final class Probe implements GameplayExtension {
        static final Map<Event,Integer> events=new EnumMap<Event,Integer>(Event.class);
        static int updates;
        static boolean customEnemies;
        public void onEvent(GameplaySession s,Event e,Object value){events.merge(e,1,Integer::sum);}
        public void update(GameplaySession s,double dt){updates++;}
        public Entity createEnemy(GameplaySession s,int wave,int index,int x,int y){
            return customEnemies?new EnemyShip(x,y,DrawManager.SpriteType.EnemyShipC1):null;
        }
        public double enemyHealth(GameplaySession s,Entity enemy,double hp){return customEnemies?hp*2:hp;}
    }
    private static Field field(Class<?> type,String name)throws Exception {
        Field f=type.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static Object get(Object target,String name)throws Exception{return field(target.getClass(),name).get(target);}
    private static void set(Object target,String name,Object value)throws Exception{field(target.getClass(),name).set(target,value);}
    private static void check(boolean value,String text){checks++;if(!value)throw new AssertionError(text);}
    private static ItemInfo equipment(String id){return ItemSystem.equipmentItems().stream().filter(i->i.itemId.equals(id)).findFirst().get();}
    private static GameplaySession session(Ship.ShipType type,String gear){
        return GameplaySession.isolated(type,Skin.RAINBOW_RUSH,gear==null?null:equipment(gear),new Random(39));
    }
    private static Run active(GameplaySession systems)throws Exception{
        Run r=new Run(448,520,new Random(42),systems);set(r,"phase",Run.Phase.PLAYING);r.update(.001,0,false);return r;
    }
    private static void grant(Run r,String id){GrantResult result=r.items.tryGrant(new GrantRequest(UUID.randomUUID().toString(),id,GrantSource.REWARD,GrantTiming.NOW));check(result.isSuccess(),"Shared grant succeeds: "+id+" / "+result.reason);}
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception {
        Path saves=Paths.get(Core.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        check(saves.getFileName().toString().startsWith("connected-check"),"Test saves must be isolated from the player's saves");
        Core.getLogger().setLevel(java.util.logging.Level.WARNING);
        loadouts();
        coldQa(saves);
        selectedLoadout();
        equipmentEffects();
        bosses();
        progressAndEconomy(saves);
        meteorsAndPause();
        registeredEnemies();
        check(Probe.events.getOrDefault(GameplayExtension.Event.START,0)>0 && Probe.updates>0,
                "ServiceLoader discovers a new feature without editing EndlessMode");
        check(Probe.events.containsKey(GameplayExtension.Event.ITEM)
                && Probe.events.containsKey(GameplayExtension.Event.WAVE_CLEARED),"Feature receives item and wave lifecycle events");
        System.out.println("PASS: "+checks+" connected gameplay checks");
    }
    private static void coldQa(Path saves)throws Exception {
        Map<Path,byte[]> before=new HashMap<Path,byte[]>();
        try(java.util.stream.Stream<Path> files=Files.list(saves)){files.filter(Files::isRegularFile).forEach(p->{try{before.put(p,Files.readAllBytes(p));}catch(Exception e){throw new RuntimeException(e);}});}
        field(PurchaseManager.class,"instance").set(null,null);
        GameplaySession qa=GameplaySession.open(true,new Random(8));active(qa);qa.finish(false,0,0);
        Set<Path> after=new HashSet<Path>();
        try(java.util.stream.Stream<Path> files=Files.list(saves)){files.filter(Files::isRegularFile).forEach(after::add);}
        check(after.equals(before.keySet()),"Cold QA creates no shared wallet, equipment or migration files");
        for(Map.Entry<Path,byte[]> entry:before.entrySet())
            check(Arrays.equals(entry.getValue(),Files.readAllBytes(entry.getKey())),"Cold QA does not rewrite existing saves");
    }
    private static void selectedLoadout()throws Exception {
        PurchaseManager purchases=PurchaseManager.getInstance();
        CurrencyManager.getInstance().addCoins(2000);
        purchases.purchase(shop.ShopCatalog.findByRef(shop.ShopCategory.SHIP,"FAST_ATTACK"),shop.Currency.COINS);
        purchases.purchase(shop.ShopCatalog.findByRef(shop.ShopCategory.SKIN,"RAINBOW_RUSH"),shop.Currency.COINS);
        purchases.purchase(shop.ShopCatalog.findByRef(shop.ShopCategory.UPGRADE,"extra_slots"),shop.Currency.COINS);
        check(purchases.selectShip(Ship.ShipType.FAST_ATTACK) && purchases.selectSkin(Skin.RAINBOW_RUSH),"Actual shop loadout is selected");
        check(purchases.setUpgradeOn("extra_slots",true),"Actual shop equipment is selected");
        GameplaySession selected=GameplaySession.open(true,new Random(1));Run r=active(selected);
        check(selected.getShip().getType()==Ship.ShipType.FAST_ATTACK && selected.getSkin()==Skin.RAINBOW_RUSH,
                "Real mode entry reads the actual shared Customize selection");
        check(r.items.getView().slots.size()>3,"Real mode entry reads purchased equipment through ItemSystem");
        selected.finish(false,0,0);
    }
    private static void registeredEnemies()throws Exception {
        Probe.customEnemies=true;
        try {
            Run r=active(session(Ship.ShipType.STANDARD,null));
            check(r.getEnemies().stream().allMatch(e->e.entity.getSpriteType()==DrawManager.SpriteType.EnemyShipC1 && e.maxHp==2),
                    "Registered new enemy factory and health rules join endless without editing its source");
        } finally { Probe.customEnemies=false; }
    }
    private static void loadouts()throws Exception {
        for(Ship.ShipType type:Ship.ShipType.values()) {
            GameplaySession systems=session(type,null);Run r=active(systems);
            double x=r.getPlayer().x;r.update(.1,1,true);
            check(Math.abs(r.getPlayer().x-x-type.getSpeed()*6)<.001,type+" uses the shared movement stat");
            check(r.getShots().stream().filter(r::isPlayerShot).count()==type.getBulletsPerShot(),type+" uses shared projectile count");
            check(Math.abs((Double)get(r,"fireRate")-1000.0/type.getShootingInterval())<.001,type+" uses shared firing interval");
            check(systems.getShip().getType()==type && systems.getSkin()==Skin.RAINBOW_RUSH,"Loadout retains native ship and animated skin");
        }
    }
    private static void equipmentEffects()throws Exception {
        Run extra=active(session(Ship.ShipType.STANDARD,"extra_slots"));
        check(extra.items.getView().slots.size()>3,"Extra Slots changes real inventory capacity");
        Run life=active(session(Ship.ShipType.STANDARD,"max_life"));grant(life,"life");grant(life,"life");
        check(life.getLives()>3,"Max Life lets native life pickups exceed the standard cap");
        Run normal=active(session(Ship.ShipType.STANDARD,null));grant(normal,"shield");normal.useItem(0);
        Run longer=active(session(Ship.ShipType.STANDARD,"long_lasting"));grant(longer,"shield");longer.useItem(0);
        long normalTime=normal.items.getView().effects.stream().filter(e->e.item.effectKind==EffectKind.SHIELD).findFirst().get().remainingMillis;
        long longerTime=longer.items.getView().effects.stream().filter(e->e.item.effectKind==EffectKind.SHIELD).findFirst().get().remainingMillis;
        check(longerTime>normalTime,"Long Lasting extends the real shield timer");
        Run magnet=active(session(Ship.ShipType.STANDARD,"magnet"));
        int events=Probe.events.getOrDefault(GameplayExtension.Event.ITEM,0);
        magnet.items.onEnemyDefeated(DropSource.SPECIAL_ENEMY,10,magnet.getPlayer().y+15);
        set(magnet,"invulnerability",0.0);for(int i=0;i<4;i++)magnet.update(.1,0,false);
        check(magnet.items.getView().drops.isEmpty() && Probe.events.getOrDefault(GameplayExtension.Event.ITEM,0)>events,
                "Magnet collects a landing drop away from the ship through the shared item system");
    }
    @SuppressWarnings("unchecked")
    private static void bosses()throws Exception {
        Run r=active(session(Ship.ShipType.STANDARD,null));
        set(r,"wave",4);((List<?>)get(r,"enemies")).clear();r.update(.001,0,false);
        check(r.getBoss().entity instanceof MidBoss,"Wave 5 uses the real campaign mid-boss");
        set(r,"phase",Run.Phase.PLAYING);
        double y=r.getBoss().y;for(int i=0;i<240;i++)r.update(1.0/60,0,false);
        check(r.getBoss().y>=138 && r.getBoss().y!=y,"Native boss bounces inside the taller endless HUD boundary");
        r=active(session(Ship.ShipType.STANDARD,null));
        set(r,"wave",9);((List<?>)get(r,"enemies")).clear();r.update(.001,0,false);
        check(r.getBoss().entity instanceof FinalBoss && r.getBoss().width==48,"Wave 10 uses final boss's actual entity and hitbox");
        if(r.getPhase()==Run.Phase.REWARD)r.chooseReward(0);
        set(r,"phase",Run.Phase.PLAYING);set(r.getBoss().entity,"targetShotsForBurst",1);
        java.util.Random fixed=new java.util.Random(){@Override public int nextInt(int bound){return 0;}};
        set(r.getBoss().entity,"random",fixed);
        set(r,"enemyShotClock",0.0);r.update(.001,0,false);
        List<Run.Body> incoming=new ArrayList<Run.Body>();for(Run.Body b:r.getShots())if(!r.isPlayerShot(b))incoming.add(b);
        check(incoming.size()==3,"Final boss delegates to the teammate's native three-bullet pattern");
        check(((Double)get(incoming.get(0),"vx"))==((Bullet)incoming.get(0).entity).getSpeedX()*60.0,
                "Native aimed horizontal bullet velocity is preserved");
        Object rain=get(r.getBoss().entity,"rainWarningCooldown");
        set(rain,"time",System.currentTimeMillis());long before=(Long)get(rain,"time");
        r.togglePause();set(r,"bossPausedAt",System.currentTimeMillis()-2000);r.togglePause();
        check((Long)get(rain,"time")-before>=1995,"Endless resume preserves the A1 boss warning timer");
    }
    private static void progressAndEconomy(Path saves)throws Exception {
        Constructor<GameplaySession> ctor=GameplaySession.class.getDeclaredConstructor(Ship.ShipType.class,Skin.class,ItemInfo.class,boolean.class,boolean.class,Random.class);
        ctor.setAccessible(true);
        GameplaySession live=ctor.newInstance(Ship.ShipType.STANDARD,Skin.CLASSIC,null,true,false,new Random(2));
        Run r=active(live);
        int coins=CurrencyManager.getInstance().getCoins(),diamonds=DiamondManager.getInstance().getDiamonds();
        int kills=Core.getAchievementManager().getTotalEnemiesKilled();
        Map<String,Boolean> campaignBadges=new HashMap<String,Boolean>();
        for(Achievement a:Core.getAchievementManager().getAchievements())
            campaignBadges.put(a.getId(),a.isUnlocked());
        EnemyShip enemy=new EnemyShip(live.getShip().getPositionX(),live.getShip().getPositionY(),DrawManager.SpriteType.EnemyShipA1);
        live.enemyDefeated(enemy,true,100);live.update(.02,Collections.emptyList(),true);
        check(enemy.isDestroyed(),"Shared defeat events expose an already-destroyed native entity");
        check(live.getCoinsEarned()==SharedAccess.coinValue(true) && CurrencyManager.getInstance().getCoins()==coins+SharedAccess.coinValue(true),
                "Picked-up coins reach the same wallet the shop spends");
        check(Core.getFileManager().loadCoins()==CurrencyManager.getInstance().getCoins(),"Shared wallet persists the earned coins");
        check(Core.getAchievementManager().getTotalEnemiesKilled()==kills+1,"Enemy defeats reach shared achievement progress");
        live.waveCleared();check(live.getPendingDiamonds()==1,"Cleared wave uses campaign's pending diamond rule");
        live.finish(true,20,1);live.finish(true,20,1);
        check(DiamondManager.getInstance().getDiamonds()==diamonds+1 && live.getBankedDiamonds()==1,"Cash-out banks diamonds exactly once");
        check(Core.getFileManager().loadDiamonds()==diamonds+1,"Diamond reward persists in the shared wallet");
        for(Achievement a:Core.getAchievementManager().getAchievements())
            if(a.getId().equals("infinity_void")||a.getRequirement()==Achievement.Requirement.LEVEL10_ALL_SHIPS)
                check(a.isUnlocked()==campaignBadges.get(a.getId()),"Endless progress does not pretend to be a campaign victory");
        GameplaySession death=ctor.newInstance(Ship.ShipType.STANDARD,Skin.CLASSIC,null,true,false,new Random(3));
        active(death);death.waveCleared();death.finish(false,10,1);
        check(death.getPendingDiamonds()==0 && DiamondManager.getInstance().getDiamonds()==diamonds+1,"Death forfeits only unbanked diamonds");
        Map<Path,byte[]> before=new HashMap<Path,byte[]>();
        try(java.util.stream.Stream<Path> files=Files.list(saves)){files.filter(Files::isRegularFile).forEach(p->{try{before.put(p,Files.readAllBytes(p));}catch(Exception e){throw new RuntimeException(e);}});}
        GameplaySession qa=session(Ship.ShipType.FAST_ATTACK,null);Run test=active(qa);
        qa.enemyDefeated(enemy,true,100);qa.waveCleared();grant(test,"shield");test.useItem(0);test.finish("CASHED OUT");
        for(Map.Entry<Path,byte[]> entry:before.entrySet())
            check(Arrays.equals(entry.getValue(),Files.readAllBytes(entry.getKey())),"QA leaves shared save unchanged: "+entry.getKey().getFileName());
        check(Core.getAchievementManager().getTotalEnemiesKilled()==kills+1,"QA does not change in-memory achievement progress either");
    }
    @SuppressWarnings("unchecked")
    private static void meteorsAndPause()throws Exception {
        GameplaySession systems=session(Ship.ShipType.STANDARD,null);Run r=active(systems);
        systems.beginWave(3,false);
        MeteorManager manager=(MeteorManager)get(systems,"meteors");
        Meteor meteor=new Meteor(true,180,448,5000,10,20,1,new Random(2));
        set(meteor,"exactX",200f);meteor.setPositionX(200);set(manager,"meteor",meteor);
        check(systems.interceptPlayerBullet(new Bullet(204,184,-6)),"Real player projectile hits the shared meteor");
        List<DiamondDrop> drops=(List<DiamondDrop>)get(manager,"drops");check(drops.size()==1,"Native meteor creates its native diamond drop");
        long age=drops.get(0).getAge();r.togglePause();for(int i=0;i<100;i++)r.update(.1,0,false);
        ((SharedAccess.Meteors)get(systems,"meteorClock")).sync(0);
        check(Math.abs(drops.get(0).getAge()-age)<=5,"Pause does not age native meteor/diamond timers");r.togglePause();
        Ship ship=systems.getShip();ship.setPositionX(drops.get(0).getPositionX());ship.setPositionY(drops.get(0).getPositionY());
        systems.update(.01,Collections.emptyList(),true);
        check(systems.getPendingDiamonds()==1,"Catching the native diamond reaches the same pending pot");
    }
    private static Object uncheckedGet(Object target,String name){try{return get(target,name);}catch(Exception e){throw new RuntimeException(e);}}
}
