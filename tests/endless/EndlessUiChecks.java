package engine;

import screen.EndlessMode;
import screen.EndlessMode.Run;
import screen.EndlessMode.Run.Phase;
import java.awt.Canvas;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import sun.misc.Unsafe;

/** Exercises actual screen input, reward transitions and rendering without touching player saves. */
public final class EndlessUiChecks {
    public static class Offscreen extends Frame {
        public Offscreen() { super(432,497); }
        @Override public Graphics getGraphics() { return new BufferedImage(432,497,BufferedImage.TYPE_INT_RGB).getGraphics(); }
        @Override public Insets getInsets() { return new Insets(0,0,0,0); }
    }
    private static int checks;
    private static final Canvas INPUT = new Canvas();
    private static Object controls;
    private static Field field(Class<?> type,String name) throws Exception {
        Field f=type.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static Object get(Class<?> type,Object target,String name) throws Exception { return field(type,name).get(target); }
    private static void set(Class<?> type,Object target,String name,Object value) throws Exception { field(type,name).set(target,value); }
    private static void check(boolean value,String message) { checks++;if(!value)throw new AssertionError(message); }
    private static void update(EndlessMode game) throws Exception {
        Method m=EndlessMode.class.getDeclaredMethod("update");m.setAccessible(true);m.invoke(game);
    }
    private static void mouse(int x,int y) {
        ((MouseListener)controls).mousePressed(new MouseEvent(INPUT,MouseEvent.MOUSE_PRESSED,0,0,x,y,1,false,MouseEvent.BUTTON1));
    }
    private static void key(int code,boolean pressed) {
        KeyEvent e=new KeyEvent(INPUT,pressed?KeyEvent.KEY_PRESSED:KeyEvent.KEY_RELEASED,0,0,code,KeyEvent.CHAR_UNDEFINED);
        if(pressed)((KeyListener)controls).keyPressed(e);else((KeyListener)controls).keyReleased(e);
    }
    private static void clearThirdWave(Run run) throws Exception {
        set(Run.class,run,"wave",3);
        ((List<?>)get(Run.class,run,"enemies")).clear();
    }
    private static void readyToChoose(EndlessMode game) throws Exception {
        // Advance the screen's real input guard without advancing the paused simulation.
        for(int i=0;i<5;i++) {
            set(EndlessMode.class,game,"previousTime",System.nanoTime()-100_000_000L);
            update(game);
        }
    }
    private static void image(String directory,String name) throws Exception {
        if(directory==null)return;
        Path dir=Path.of(directory);Files.createDirectories(dir);
        ImageIO.write((BufferedImage)get(DrawManager.class,null,"backBuffer"),"png",dir.resolve(name+".png").toFile());
    }
    public static void main(String[] args) throws Exception {
        Unsafe unsafe=(Unsafe)get(Unsafe.class,null,"theUnsafe");
        Core.getDrawManager().setFrame((Frame)unsafe.allocateInstance(Offscreen.class));
        Constructor<EndlessMode> ctor=EndlessMode.class.getDeclaredConstructor(int.class,int.class,boolean.class);ctor.setAccessible(true);
        EndlessMode game=ctor.newInstance(432,497,true);game.initialize();
        controls=get(EndlessMode.class,null,"controls");
        Run run=(Run)get(EndlessMode.class,game,"run");
        set(Run.class,run,"phase",Phase.PLAYING);

        mouse(100,155);update(game); // Focus/click while fighting, not on a reward screen.
        clearThirdWave(run);update(game);update(game);
        check(run.getPhase()==Phase.REWARD,"Combat clicks must not silently claim the next wave reward");
        long seconds=run.getSeconds(),score=run.getScore();
        readyToChoose(game);
        check(run.getPhase()==Phase.REWARD,"Reward remains visible until an explicit new choice");
        check(run.getSeconds()==seconds && run.getScore()==score,"Reward screen freezes survival and scoring");
        String directory=args.length==0?null:args[0];image(directory,"reward");
        key(KeyEvent.VK_R,true);update(game);key(KeyEvent.VK_R,false);
        check((Boolean)get(Run.class,run,"refreshed"),"Fresh R press rerolls the visible offers");
        check(!run.refreshRewards(),"Only one reroll per checkpoint");
        key(KeyEvent.VK_2,true);update(game);key(KeyEvent.VK_2,false);
        check(run.getPhase()==Phase.INTERMISSION,"Fresh reward key applies one choice and resumes safely");

        // A key held across a transition must be released, rather than selecting an unseen card.
        set(Run.class,run,"phase",Phase.PLAYING);key(KeyEvent.VK_1,true);update(game);
        clearThirdWave(run);update(game);readyToChoose(game);
        key(KeyEvent.VK_1,true);update(game); // OS key-repeat while still held.
        check(run.getPhase()==Phase.REWARD,"Held item key cannot choose a new reward");
        key(KeyEvent.VK_1,false);key(KeyEvent.VK_1,true);update(game);key(KeyEvent.VK_1,false);
        check(run.getPhase()==Phase.INTERMISSION,"Release and press chooses normally");

        set(Run.class,run,"phase",Phase.PLAYING);
        key(KeyEvent.VK_R,true);update(game);key(KeyEvent.VK_R,false);
        clearThirdWave(run);update(game);readyToChoose(game);
        check(!(Boolean)get(Run.class,run,"refreshed"),"Combat R cannot spend a future checkpoint's reroll");
        mouse(2,20);update(game);
        check(run.getPhase()==Phase.REWARD,"Clicking outside cards leaves the choice open");
        mouse(100,240);update(game);
        check(run.getPhase()==Phase.INTERMISSION,"New mouse click selects the visible card");

        set(Run.class,run,"phase",Phase.PLAYING);set(Run.class,run,"lives",3);update(game);image(directory,"hud-full");
        set(Run.class,run,"lives",1);update(game);image(directory,"hud-critical");
        Run.Body target=run.getEnemies().get(0);target.hp=4;target.x=200;target.y=300;
        set(Run.class,run,"shotClock",0.0);run.update(.001,0,true);
        Run.Body shot=run.getShots().stream().filter(run::isPlayerShot).findFirst().get();
        shot.x=target.x+4;shot.y=target.y+4;run.update(.001,0,false);
        check(!((List<?>)get(EndlessMode.class,game,"hits")).isEmpty(),"Enemy collision creates damage numbers");
        update(game);image(directory,"hit-feedback");

        set(Run.class,run,"wave",4);((List<?>)get(Run.class,run,"enemies")).clear();update(game);
        check(run.getWave()==5 && run.getPhase()==Phase.BOSS_INTRO,"Fifth wave opens the boss arrival panel");
        check(run.getBoss().maxHp>0 && run.getBossDamage()==1,"Arrival panel has actual boss health and damage");
        for(int i=0;i<10;i++) {
            set(EndlessMode.class,game,"previousTime",System.nanoTime()-100_000_000L);update(game);
        }
        image(directory,"boss-intro");
        key(KeyEvent.VK_P,true);update(game);key(KeyEvent.VK_P,false);
        check(run.getPhase()==Phase.PAUSED,"Boss introduction can be paused");
        key(KeyEvent.VK_P,true);update(game);key(KeyEvent.VK_P,false);
        while(run.getPhase()==Phase.BOSS_INTRO) {
            set(EndlessMode.class,game,"previousTime",System.nanoTime()-100_000_000L);update(game);
        }
        image(directory,"hud-boss");
        check(run.getBoss()!=null && run.getPhase()==Phase.PLAYING,"Opening returns to playable boss duel");
        key(KeyEvent.VK_P,true);update(game);key(KeyEvent.VK_P,false);image(directory,"pause-connected");
        check(run.getPhase()==Phase.PAUSED,"Shared-service menu keeps the arena paused");
        set(Run.class,run,"phase",Phase.PLAYING);set(Run.class,run,"wave",9);set(Run.class,run,"lives",3);
        ((List<?>)get(Run.class,run,"enemies")).clear();update(game);
        if(run.getPhase()==Phase.REWARD)run.chooseReward(0);
        update(game);image(directory,"final-boss-intro");
        check(run.getBoss().entity instanceof entity.FinalBoss,"Arrival draws the shared final boss model");
        Class<?> recordType=Class.forName("screen.EndlessMode$EndlessRecordsScreen");
        Constructor<?> recordCtor=recordType.getDeclaredConstructor(int.class,int.class,boolean.class);recordCtor.setAccessible(true);
        Object scores=recordCtor.newInstance(432,497,true);
        Method scoreUpdate=recordType.getDeclaredMethod("update");scoreUpdate.setAccessible(true);scoreUpdate.invoke(scores);
        image(directory,"highscores-endless");
        check(recordType.getEnclosingClass()==EndlessMode.class,"Endless record view stays inside the endless module");
        System.out.println("PASS: "+checks+" screen input / reward checks");
    }
}
