package engine;

import endless.*;
import entity.*;
import screen.EndlessMode;
import screen.EndlessMode.Run;
import shop.*;
import java.awt.Canvas;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import sun.misc.Unsafe;

/** Actual shop selection, native VFX, screen wiring and QA isolation. Disposable saves only. */
public final class ShopEffectsChecks {
    @SuppressWarnings("serial")
    public static final class Offscreen extends Frame {
        public Offscreen(){super(432,497);}
        @Override public Graphics getGraphics(){return new BufferedImage(432,497,BufferedImage.TYPE_INT_RGB).getGraphics();}
        @Override public Insets getInsets(){return new Insets(0,0,0,0);}
    }
    private static final String[] EFFECTS={"comet_trail","ghost_trail","lightning_chain","warp_in"};
    private static int checks;
    private static Field field(Class<?> type,String name)throws Exception {
        Field f=type.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static Object get(Object target,String name)throws Exception{return field(target.getClass(),name).get(target);}
    private static void set(Object target,String name,Object value)throws Exception{field(target.getClass(),name).set(target,value);}
    private static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    private static void tick(EndlessMode game)throws Exception {
        set(game,"previousTime",System.nanoTime()-50_000_000L);
        Method update=EndlessMode.class.getDeclaredMethod("update");update.setAccessible(true);update.invoke(game);
    }
    private static Map<Path,byte[]> snapshot(Path dir)throws Exception {
        Map<Path,byte[]> result=new HashMap<>();
        try(var paths=Files.list(dir)) {
            for(Path p:paths.filter(Files::isRegularFile).toList())result.put(p,Files.readAllBytes(p));
        }
        return result;
    }
    private static void unchanged(Map<Path,byte[]> before,Path dir)throws Exception {
        Map<Path,byte[]> after=snapshot(dir);
        check(after.keySet().equals(before.keySet()),"QA creates no shared save files");
        for(Path p:before.keySet())check(Arrays.equals(before.get(p),after.get(p)),"QA leaves "+p.getFileName()+" unchanged");
    }
    private static BufferedImage buffer() {return (BufferedImage)SharedAccess.get(DrawManager.class,"backBuffer");}
    private static void image(Path directory,String name)throws Exception {
        Files.createDirectories(directory);ImageIO.write(buffer(),"png",directory.resolve(name+".png").toFile());
    }
    private static int lit(int top,int bottom) {
        BufferedImage b=buffer();int count=0;
        for(int y=top;y<bottom;y++)for(int x=0;x<b.getWidth();x++)if((b.getRGB(x,y)&0xffffff)!=0)count++;
        return count;
    }
    public static void main(String[] args)throws Exception {
        Path saves=Path.of(Core.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        check(saves.getFileName().toString().startsWith("connected-check"),"Disposable profile required");
        Core.getLogger().setLevel(java.util.logging.Level.WARNING);
        PurchaseManager purchases=PurchaseManager.getInstance();
        ShopEffects none=GameplaySession.open(true,new Random(1)).getShopEffects();
        check(!(Boolean)get(none,"comet") && get(none,"ghost")==null && get(none,"lightning")==null && get(none,"warp")==null,
                "Unpurchased effects never activate");
        CurrencyManager.getInstance().addCoins(2000);
        for(String ref:EFFECTS) {
            ShopItem item=ShopCatalog.findByRef(ShopCategory.EFFECT,ref);
            purchases.purchase(item,shop.Currency.COINS);
            check(purchases.isEffectOn(item),"Shared shop purchase enables "+ref);
        }
        ShopItem ghostItem=ShopCatalog.findByRef(ShopCategory.EFFECT,"ghost_trail");
        purchases.toggleEffect(ghostItem);
        check(get(GameplaySession.open(true,new Random(2)).getShopEffects(),"ghost")==null,"Customize OFF is honored on the next run");
        purchases.toggleEffect(ghostItem);
        Map<Path,byte[]> savesBefore=snapshot(saves);
        field(PurchaseManager.class,"instance").set(null,null);
        SharedAccess.Loadout cold=SharedAccess.loadout(true);
        check(cold.effects.equals(new LinkedHashSet<>(Arrays.asList(EFFECTS))),"Cold QA reads all selected effects without loading/migrating the shop");
        check(field(PurchaseManager.class,"instance").get(null)==null,"QA selection read does not initialize the purchase manager");
        unchanged(savesBefore,saves);

        Unsafe unsafe=(Unsafe)field(Unsafe.class,"theUnsafe").get(null);
        Core.getDrawManager().setFrame((Frame)unsafe.allocateInstance(Offscreen.class));
        Constructor<EndlessMode> ctor=EndlessMode.class.getDeclaredConstructor(int.class,int.class,boolean.class);ctor.setAccessible(true);
        EndlessMode game=ctor.newInstance(432,497,true);game.initialize();
        GameplaySession session=(GameplaySession)get(game,"systems");Run run=(Run)get(game,"run");ShopEffects effects=session.getShopEffects();
        check((Boolean)get(effects,"comet") && get(effects,"ghost") instanceof GhostTrail
                && get(effects,"lightning") instanceof LightningChain && get(effects,"warp") instanceof WarpIn,
                "Real Endless entry instantiates the four native effects");
        Path screenshots=Path.of(args[0]);
        for(int i=0;i<22;i++)tick(game);
        check(effects.hidesShip(),"Native arrival draws the ship during the safe countdown");
        image(screenshots,"shop-warp-arrival");
        set(run,"phase",Run.Phase.PLAYING);set(run,"invulnerability",1000.0);set(run,"enemyShotClock",1000.0);
        KeyListener keys=(KeyListener)field(EndlessMode.class,"controls").get(null);Canvas input=new Canvas();
        keys.keyPressed(new KeyEvent(input,KeyEvent.KEY_PRESSED,0,0,KeyEvent.VK_RIGHT,KeyEvent.CHAR_UNDEFINED));
        keys.keyPressed(new KeyEvent(input,KeyEvent.KEY_PRESSED,0,0,KeyEvent.VK_SPACE,KeyEvent.CHAR_UNDEFINED));
        for(int i=0;i<20;i++)tick(game);
        keys.keyReleased(new KeyEvent(input,KeyEvent.KEY_RELEASED,0,0,KeyEvent.VK_RIGHT,KeyEvent.CHAR_UNDEFINED));
        keys.keyReleased(new KeyEvent(input,KeyEvent.KEY_RELEASED,0,0,KeyEvent.VK_SPACE,KeyEvent.CHAR_UNDEFINED));
        GhostTrail ghost=(GhostTrail)get(effects,"ghost");
        check((Integer)get(ghost,"count")>4 && ghost.isMoving(),"Moving in the real screen records and renders native ship ghosts");
        check(!effects.hidesShip(),"Cosmetic arrival cannot hide a vulnerable combat ship");
        image(screenshots,"shop-effects-playing");

        Core.getDrawManager().initDrawing(game);
        Graphics2D graphics=(Graphics2D)SharedAccess.get(DrawManager.class,"backBufferGraphics");
        java.awt.geom.AffineTransform transform=graphics.getTransform();
        effects.drawBullet(Core.getDrawManager(),new Bullet(200,250,-6));
        check(lit(250,300)>30,"Native comet renderer produces visible pixels below a player projectile");
        check(lit(0,130)==0 && graphics.getTransform().equals(transform),"Effect drawing preserves the HUD and original Graphics state");
        image(screenshots,"shop-comet");

        java.util.List<Entity> enemies=new ArrayList<>();for(Run.Body b:run.getEnemies())enemies.add(b.entity);
        session.update(0,enemies,true);Entity dead=enemies.get(0);double hp=run.getEnemies().get(1).hp;
        session.enemyDefeated(dead,false,100);
        LightningChain lightning=(LightningChain)get(effects,"lightning");
        check(lightning.getStreak()==1 && !((java.util.List<?>)get(lightning,"strikes")).isEmpty(),"Actual defeat callback creates the native lightning chain");
        check(run.getEnemies().get(1).hp==hp,"Lightning remains cosmetic and does not damage neighboring enemies");
        Core.getDrawManager().initDrawing(game);effects.drawLightning(Core.getDrawManager());
        check(lit(130,497)>0 && lit(0,130)==0,"Lightning is visible below the HUD");image(screenshots,"shop-lightning");
        Object strike=((java.util.List<?>)get(lightning,"strikes")).get(0);
        long born=(Long)get(strike,"born");Object clock=get(effects,"clock");
        set(clock,"last",System.currentTimeMillis()-2000);
        effects.frame(session.getShip(),false,false,3,0,.1);
        check((Long)get(strike,"born")-born>=1995 && lightning.getStreak()==1,"Paused time does not expire native lightning or its streak");
        check((Integer)get(ghost,"count")==0,"Pause clears ghost history");
        Core.getDrawManager().initDrawing(game);effects.drawLightning(Core.getDrawManager());effects.drawWarp(Core.getDrawManager(),session.getShip(),session.getShip().getColor());
        check(lit(0,497)==0 && !effects.hidesShip(),"Pause hides visual effects without hiding the ship on resume");
        session.finish(false,0,0);
        check(lightning.getStreak()==0 && ((java.util.List<?>)get(lightning,"strikes")).isEmpty(),"Run finish clears native effect state");
        unchanged(savesBefore,saves);
        purchases=PurchaseManager.getInstance();
        for(String ref:EFFECTS)purchases.toggleEffect(ShopCatalog.findByRef(ShopCategory.EFFECT,ref));
        ShopEffects off=GameplaySession.open(true,new Random(3)).getShopEffects();
        check(!(Boolean)get(off,"comet") && get(off,"ghost")==null && get(off,"lightning")==null && get(off,"warp")==null,
                "Turning all four effects off disables them in the next Endless run");
        System.out.println("PASS: "+checks+" shared shop effect checks");
    }
}
