package endless;

import engine.*;
import entity.*;
import item.ItemAPI;
import item.ItemAPI.*;
import item.ItemManager;
import item.ItemSystem;
import shop.*;
import java.awt.Color;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;

/** Compatibility adapters owned exclusively by endless; the original game files stay untouched. */
public final class SharedAccess {
    private SharedAccess() { }

    private static Field field(Class<?> type,String name) {
        for(Class<?> c=type;c!=null;c=c.getSuperclass())try {
            Field f=c.getDeclaredField(name);f.setAccessible(true);return f;
        }catch(NoSuchFieldException ignored){ }
        throw new IllegalStateException("Shared API field missing: "+type.getName()+"."+name);
    }
    public static Object get(Object target,String name) {
        try{return field(target instanceof Class<?>?(Class<?>)target:target.getClass(),name).get(target instanceof Class<?>?null:target);}
        catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    private static void set(Object target,String name,Object value) {
        try{field(target instanceof Class<?>?(Class<?>)target:target.getClass(),name).set(target instanceof Class<?>?null:target,value);}
        catch(IllegalAccessException e){throw new IllegalStateException(e);}
    }
    private static Object call(Object target,String name,Class<?>[] types,Object... args) {
        try {
            Class<?> type=target instanceof Class<?>?(Class<?>)target:target.getClass();
            Method method=type.getDeclaredMethod(name,types);method.setAccessible(true);
            return method.invoke(target instanceof Class<?>?null:target,args);
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Shared API call failed: "+name,e);}
    }
    private static int number(Object target,String name){return ((Number)get(target,name)).intValue();}

    public static final class Loadout {
        public Ship.ShipType ship=Ship.ShipType.STANDARD;
        public Skin skin=Skin.CLASSIC;
        public ShapeSkin shape;
        public ItemInfo equipment;
        public final Set<String> effects=new LinkedHashSet<String>();
    }

    /** Existing manager when available; a cold QA launch reads its native save without running migrations. */
    public static Loadout loadout(boolean testing) {
        PurchaseManager purchases=testing?(PurchaseManager)get(PurchaseManager.class,"instance"):PurchaseManager.getInstance();
        Loadout result=new Loadout();
        if(purchases!=null) {
            result.ship=purchases.getSelectedShip();result.skin=purchases.getSelectedSkin();
            result.shape=purchases.getSelectedShape(result.ship);
            for(ItemInfo item:ItemSystem.equipmentItems())if(purchases.isUpgradeOn(item.itemId)){result.equipment=item;break;}
            for(ShopItem item:ShopCatalog.getByCategory(ShopCategory.EFFECT))
                if(purchases.isEffectOn(item))result.effects.add(item.getRef());
            return result;
        }
        try {
            File equipmentFile=(File)call(ItemSystem.class,"equipmentFile",new Class<?>[0]);
            File ownedFile=new File(equipmentFile.getParentFile(),(String)get(PurchaseManager.class,"OWNED_FILE"));
            Properties saved=new Properties();
            if(ownedFile.isFile())try(Reader reader=Files.newBufferedReader(ownedFile.toPath())){saved.load(reader);}
            String prefix=(String)get(PurchaseManager.class,"OWNED_PREFIX");
            String ship=saved.getProperty((String)get(PurchaseManager.class,"SHIP_KEY"),result.ship.name());
            String skin=saved.getProperty((String)get(PurchaseManager.class,"SKIN_KEY"),result.skin.name());
            Ship.ShipType candidate=Ship.ShipType.valueOf(ship);
            Skin appearance=Skin.valueOf(skin);
            if(owned(saved,prefix,ShopCatalog.findByRef(ShopCategory.SHIP,candidate.name())))result.ship=candidate;
            if(owned(saved,prefix,ShopCatalog.findByRef(ShopCategory.SKIN,appearance.name())))result.skin=appearance;
            String shapeName=saved.getProperty((String)get(PurchaseManager.class,"SHAPE_KEY")+result.ship.name());
            if(shapeName!=null) {
                ShapeSkin shape=ShapeSkin.valueOf(shapeName.trim());
                if(shape.getShipType()==result.ship && owned(saved,prefix,ShopCatalog.findByRef(ShopCategory.SHAPE,shape.name())))result.shape=shape;
            }
            Set<String> off=new HashSet<String>(Arrays.asList(saved.getProperty((String)get(PurchaseManager.class,"OFF_KEY"),"").split(",")));
            for(ShopItem entry:ShopCatalog.getByCategory(ShopCategory.EFFECT))
                if(owned(saved,prefix,entry) && !off.contains(entry.getId()))result.effects.add(entry.getRef());
            for(ItemInfo item:ItemSystem.equipmentItems()) {
                ShopItem entry=ShopCatalog.findByRef(ShopCategory.UPGRADE,item.itemId);
                if(entry!=null && owned(saved,prefix,entry) && !off.contains(entry.getId())){result.equipment=item;break;}
            }
            if(result.equipment==null && !Boolean.parseBoolean(saved.getProperty((String)get(PurchaseManager.class,"MIGRATED_KEY"),"false")))
                result.equipment=ItemSystem.equippedItem();
        }catch(IOException|IllegalArgumentException e){Core.getLogger().warning("Cannot read QA loadout; using defaults.");}
        return result;
    }
    private static boolean owned(Properties saved,String prefix,ShopItem entry) {
        return entry==null || Integer.parseInt(saved.getProperty(prefix+entry.getId(),"0"))>0;
    }

    /** Draw native effects in an isolated context without leaking their clip or paint to the HUD. */
    static void drawWith(java.awt.Graphics2D graphics, Runnable drawing) {
        Object previous=get(DrawManager.class,"backBufferGraphics");
        try { set(DrawManager.class,"backBufferGraphics",graphics);drawing.run(); }
        finally { set(DrawManager.class,"backBufferGraphics",previous); }
    }
    /** Adapt native visual timers to active time without changing the shared VFX classes. */
    static void shiftVisualTime(Object target,String name,long amount) {
        long value=((Number)get(target,name)).longValue();
        if(value>0)set(target,name,value+amount);
    }

    /** Independent instance of the original item implementation, including its equipment and callbacks. */
    public static final class Items {
        private final ItemSystem nativeSystem;
        private final ItemManager manager;
        @SuppressWarnings("unchecked")
        public Items(Random random,ItemInfo equipment,boolean persistent) {
            synchronized(ItemSystem.class) {
                ItemSystem.equippedItem(); // Load the existing in-memory selection before temporarily borrowing its factory.
                Object previous=get(ItemSystem.class,"equippedId");
                try {
                    set(ItemSystem.class,"equippedId",equipment==null?null:equipment.itemId);
                    Constructor<ItemSystem> constructor=ItemSystem.class.getDeclaredConstructor(Random.class);
                    constructor.setAccessible(true);nativeSystem=constructor.newInstance(random);
                }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot create an independent shared item run",e);}
                finally {set(ItemSystem.class,"equippedId",previous);}
            }
            manager=(ItemManager)get(nativeSystem,"manager");
            if(!persistent) {
                Consumer<ItemEvent> listener=(Consumer<ItemEvent>)get(manager,"listener");
                call(manager,"setEventListener",new Class<?>[]{Consumer.class},(Consumer<ItemEvent>)event->{
                    if(event.type!=EventType.ITEM_USED)listener.accept(event);
                });
            }
        }
        public ItemSystem nativeSystem(){return nativeSystem;}
        public ItemAPI api(){return nativeSystem.api();}
        public int getMaxLives(){return number(nativeSystem,"maxLives");}
        public int getStartingLives(){return number(get(nativeSystem,"balance"),"maxLives");}
        public int getLifeCapBonusScore(){return number(get(nativeSystem,"balance"),"lifeCapBonusScore");}
        public ItemInfo equippedThisRun(){return nativeSystem.equippedThisRun();}
        public UseResult useSlot(int slot){return nativeSystem.useSlot(slot);}
        public void endLevel(){nativeSystem.endLevel();}
        public void advance(long milliseconds,PlayerSnapshot player){manager.update(milliseconds,player);}
        public void beginRun(String id,int width,int top,double floorY,ItemSystem.GameHooks hooks) {
            nativeSystem.beginLevel(1,width,new Entity(0,(int)floorY-1,1,1,Color.GREEN),hooks);
            LevelRules original=(LevelRules)get(manager,"rules");
            Map<DropSource,DropRule> rules=new EnumMap<DropSource,DropRule>(DropSource.class);
            for(Map.Entry<DropSource,DropRule> entry:original.dropRules.entrySet()) {
                DropRule rule=entry.getValue();Map<String,Double> weights=new LinkedHashMap<String,Double>(rule.weights);
                for(ItemInfo item:api().getItemInfos())if(item.activationMode!=ActivationMode.EQUIPPED)weights.putIfAbsent(item.itemId,1.0);
                rules.put(entry.getKey(),new DropRule(rule.probability,weights,rule.stackDecay));
            }
            manager.endLevel();
            manager.beginLevel(new LevelRules(id,original.left,original.right,top,original.floorY,
                    original.fallSpeed,original.pickupWidth,original.pickupHeight,original.groundLifetimeMillis,rules),
                    (LifePort)get(nativeSystem,"lifePort"));
        }
    }

    /** Use the actual ship's shoot method; endless owns only the active-time scheduling. */
    public static List<Bullet> volley(Ship ship) {
        set(get(ship,"shootingCooldown"),"time",0L);
        Set<Bullet> bullets=new LinkedHashSet<Bullet>();ship.shoot(bullets);
        return new ArrayList<Bullet>(bullets);
    }
    public static int bossHealth(Boss boss){return number(boss,"hp");}
    public static int bossDamage(Boss boss){return 1;}
    public static String bossName(Boss boss){return boss.getClass().getSimpleName().replaceAll("([a-z])([A-Z])","$1 $2");}
    public static int bossInterval(Boss boss){
        try{return number(boss,"shootingInterval");}catch(IllegalStateException ignored){return number(boss,"currentShootingFrequency");}
    }
    public static void bossVolley(Boss boss,Set<Bullet> bullets,GameSettings settings) {
        Object cooldown=get(boss,"shootCooldown");
        if(cooldown==null){boss.shoot(bullets,settings);cooldown=get(boss,"shootCooldown");}
        set(cooldown,"time",0L);boss.shoot(bullets,settings);
    }
    public static void moveBoss(Boss boss,int width,int height,int top,GameSettings settings) {
        Entity model=(Entity)boss;
        int offset=top-number(boss.getClass(),"TOP_BOUNDARY");
        model.setPositionY(model.getPositionY()-offset);
        boss.moveBoss(width,height-offset,settings);
        model.setPositionY(model.getPositionY()+offset);
    }
    public static void drawBossWarnings(Boss boss,DrawManager drawing,int top) {
        java.awt.Graphics2D original=(java.awt.Graphics2D)get(DrawManager.class,"backBufferGraphics");
        java.awt.Graphics2D copy=(java.awt.Graphics2D)original.create();
        copy.clipRect(0,top,10000,10000);
        copy.translate(0,top-number(boss.getClass(),"TOP_BOUNDARY"));
        try {drawWith(copy,()->boss.drawWarnings(drawing));}finally{copy.dispose();}
    }
    public static int coinValue(boolean bonus){return number(screen.GameScreen.class,bonus?"BONUS_COIN_VALUE":"COIN_VALUE");}

    /** Adapt wall-clock effects to active endless time without editing their native physics. */
    public static final class Meteors {
        public final MeteorManager nativeManager;
        private long lastWall=System.currentTimeMillis();
        private double remainder;
        public Meteors(int stage,int width,int height,int top){nativeManager=new MeteorManager(stage,width,height,top);}
        @SuppressWarnings("unchecked")
        public void sync(double activeSeconds) {
            long now=System.currentTimeMillis();remainder+=activeSeconds*1000;
            long step=(long)remainder;remainder-=step;
            long shift=now-lastWall-step;lastWall=now;
            Object spawner=get(nativeManager,"spawner");shift(spawner,"playStart",shift);shift(spawner,"nextSpawnAt",shift);
            Object meteor=get(nativeManager,"meteor");
            if(meteor!=null){shift(meteor,"lastUpdate",shift);shift(meteor,"burstStart",shift);}
            for(DiamondDrop drop:(List<DiamondDrop>)get(nativeManager,"drops")) {
                shift(drop,"lastUpdate",shift);shift(drop,"createdAt",shift);shift(drop,"caughtAt",shift);
            }
        }
        private static void shift(Object target,String name,long shift){
            long value=((Number)get(target,name)).longValue();if(value!=0)set(target,name,value+shift);
        }
    }
}
