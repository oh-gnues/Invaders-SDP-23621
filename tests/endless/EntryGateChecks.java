package engine;

import screen.EndlessMode;
import screen.Screen;
import screen.TitleScreen;
import java.awt.Canvas;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import sun.misc.Unsafe;

/** Runs from a JAR in a source-free working directory, using disposable saves. */
public final class EntryGateChecks {
    @SuppressWarnings("serial")
    public static final class Offscreen extends Frame {
        public Offscreen() { super(432,497); }
        @Override public Graphics getGraphics() { return new BufferedImage(432,497,BufferedImage.TYPE_INT_RGB).getGraphics(); }
        @Override public Insets getInsets() { return new Insets(0,0,0,0); }
    }
    private static int checks;
    private static final Canvas INPUT=new Canvas();
    private static Object controls;
    private static TitleScreen menu;
    private static Field field(Class<?> type,String name)throws Exception {
        Field f=type.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static Object get(Class<?> type,Object target,String name)throws Exception { return field(type,name).get(target); }
    private static void set(Class<?> type,Object target,String name,Object value)throws Exception { field(type,name).set(target,value); }
    private static void check(boolean value,String message) { checks++;if(!value)throw new AssertionError(message); }
    private static void tap(int code) {
        KeyListener listener=(KeyListener)controls;
        listener.keyPressed(new KeyEvent(INPUT,KeyEvent.KEY_PRESSED,0,0,code,KeyEvent.CHAR_UNDEFINED));
        listener.keyReleased(new KeyEvent(INPUT,KeyEvent.KEY_RELEASED,0,0,code,KeyEvent.CHAR_UNDEFINED));
    }
    private static void click(int x,int y) {
        ((MouseListener)controls).mousePressed(new MouseEvent(INPUT,MouseEvent.MOUSE_PRESSED,0,0,x+16,y+55,1,false,MouseEvent.BUTTON1));
    }
    private static void update()throws Exception { EndlessMode.updateEntry(menu); }
    private static Object password()throws Exception { return get(EndlessMode.class,null,"testPassword"); }
    private static boolean promptOpen()throws Exception { return (Boolean)get(password().getClass(),password(),"open"); }
    private static String value()throws Exception { return (String)get(password().getClass(),password(),"value"); }
    private static void render(Path directory,String name)throws Exception {
        Core.getDrawManager().initDrawing(menu);EndlessMode.drawEntry(menu);
        ImageIO.write((BufferedImage)get(DrawManager.class,null,"backBuffer"),"png",directory.resolve(name+".png").toFile());
    }
    public static void main(String[] args)throws Exception {
        check(!Files.exists(Path.of("src")),"Entry checks must run without a source directory");
        Path location=Path.of(Core.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        check(location.toString().endsWith(".jar"),"Check the actual packaged classpath");
        check(location.getParent().getFileName().toString().startsWith("connected-check"),"Packaged saves are isolated");
        Unsafe unsafe=(Unsafe)get(Unsafe.class,null,"theUnsafe");
        Frame frame=(Frame)unsafe.allocateInstance(Offscreen.class);
        Core.getDrawManager().setFrame(frame);
        menu=new TitleScreen(432,497,60);
        // No native window/listener registration is needed for injected offscreen input.
        set(EndlessMode.class,null,"boundFrame",frame);
        set(EndlessMode.class,null,"menuScreen",menu);
        controls=get(EndlessMode.class,null,"controls");
        Core.getDrawManager().initDrawing(menu);
        Method hook=TitleScreen.class.getDeclaredMethod("optionalEndless",String.class);hook.setAccessible(true);
        check(Boolean.TRUE.equals(hook.invoke(menu,"drawEntry")),"Compiled Endless entry works without source files");
        try(URLClassLoader absent=new URLClassLoader(new URL[]{location.toUri().toURL()},null){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                if(name.startsWith("screen.EndlessMode"))throw new ClassNotFoundException(name);
                return super.loadClass(name,resolve);
            }
        }) {
            Class<?> title=absent.loadClass("screen.TitleScreen");
            Object base=title.getConstructor(int.class,int.class,int.class).newInstance(432,497,60);
            Method optional=title.getDeclaredMethod("optionalEndless",String.class);optional.setAccessible(true);
            check(Boolean.FALSE.equals(optional.invoke(base,"drawEntry")),"Base menu remains usable if the add-on class is absent");
        }
        check(!EndlessMode.unlocked(),"Fresh saves keep normal Endless locked");
        tap(KeyEvent.VK_E);update();
        check((Boolean)get(EndlessMode.class,null,"gateOpen"),"Shortcut opens the locked panel");
        tap(KeyEvent.VK_ENTER);update();
        check(get(Frame.class,frame,"currentScreen")==null,"Enter cannot bypass the normal unlock");
        Path screenshots=Path.of(args[0]);Files.createDirectories(screenshots);
        render(screenshots,"entry-locked");
        click(360,337);update();
        check(promptOpen() && get(Frame.class,frame,"currentScreen")==null,"Lock click opens a prompt without starting QA");
        tap(KeyEvent.VK_ENTER);update();
        check(promptOpen() && get(Frame.class,frame,"currentScreen")==null,"Empty password is rejected");
        tap(KeyEvent.VK_1);tap(KeyEvent.VK_2);tap(KeyEvent.VK_3);tap(KeyEvent.VK_ENTER);update();
        check((Boolean)get(password().getClass(),password(),"error") && value().isEmpty(),"Wrong password clears input and shows an error");
        check(get(Frame.class,frame,"currentScreen")==null,"Wrong password never launches a game");
        render(screenshots,"entry-password-error");
        tap(KeyEvent.VK_0);tap(KeyEvent.VK_1);tap(KeyEvent.VK_BACK_SPACE);tap(KeyEvent.VK_NUMPAD0);update();
        check(value().equals("00"),"Ordered digits, numpad and backspace edit the masked password");
        tap(KeyEvent.VK_DELETE);update();check(value().isEmpty(),"Delete clears the password");
        tap(KeyEvent.VK_0);tap(KeyEvent.VK_ESCAPE);update();
        check(!promptOpen() && (Boolean)get(EndlessMode.class,null,"gateOpen"),"Escape cancels only the password prompt");
        click(360,337);update();check(value().isEmpty(),"Reopening starts with an empty password");
        tap(KeyEvent.VK_0);tap(KeyEvent.VK_0);tap(KeyEvent.VK_0);update();
        render(screenshots,"entry-password");
        AtomicReference<Throwable> failure=new AtomicReference<>();
        tap(KeyEvent.VK_ENTER);
        Thread launch=new Thread(()->{try{update();}catch(Throwable e){failure.set(e);}},"entry-check");
        launch.setDaemon(true);launch.start();
        long deadline=System.nanoTime()+5_000_000_000L;
        Screen active=null;
        while(launch.isAlive() && System.nanoTime()<deadline){
            active=(Screen)get(Frame.class,frame,"currentScreen");
            if(active!=null && (Boolean)get(Screen.class,active,"isRunning"))break;
            Thread.sleep(10);
        }
        check(active instanceof EndlessMode,"Correct password launches the real Endless screen");
        check((Boolean)get(EndlessMode.class,active,"testing"),"Password access always launches nonpersistent QA");
        set(Screen.class,active,"isRunning",false);launch.join(2000);
        check(!launch.isAlive() && failure.get()==null,"Password launch returns without errors");
        check(!EndlessMode.unlocked(),"QA password never unlocks the campaign achievement");
        tap(KeyEvent.VK_E);update();click(360,337);update();
        check(promptOpen() && value().isEmpty(),"The next test run requires the password again");
        click(50,337);update();check(!promptOpen(),"BACK cancels the password prompt");
        set(EndlessMode.class,null,"gateAvailable",true);
        render(screenshots,"entry-unlocked");
        check(!(Boolean)get(password().getClass(),password(),"open"),"Normal unlocked entry has no password prompt");
        System.out.println("PASS: "+checks+" packaged entry / password checks");
    }
}
