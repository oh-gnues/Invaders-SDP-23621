package screen;

import engine.*;
import endless.*;
import entity.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import item.ItemAPI;
import item.ItemAPI.*;
import shop.Skin;
import item.ItemSystem;

/**
 * Removable local add-on: click the menu entry (or press E).
 * Requires the original infinity_void achievement; the bottom-right lock is a
 * password-protected tester bypass and writes only endless-test-records.properties.
 * Base score = active whole seconds * 10 + defeated enemies * 100; items can add bonuses.
 * Waves 5, 10, 15, ... are boss encounters with a safe three-second introduction.
 * Boss kills drop supplies but grant no automatic upgrades. Every three cleared
 * waves offer three choices, with one optional reroll; exactly one is claimed.
 * Shared features are connected through GameplaySession and ItemSystem. QA never awards progress.
 * Omit the compiled add-on to remove the entry; TitleScreen has no direct
 * compile dependency. Recompile after edits with javac -encoding UTF-8.
 */
public final class EndlessMode extends Screen {
    private static final Font UI_FONT = loadUiFont();
    private static final Color MUTED = new Color(105,135,113);
    private static final Color HUD_PANEL = new Color(3,16,11);
    private static final Color HUD_BORDER = new Color(41,102,66);
    private static final Color HUD_LABEL = new Color(155,188,166);
    private static final Color GOLD = new Color(255,214,92);
    private static final Color DANGER = new Color(255,91,101);
    private static Font loadUiFont() {
        try { return Core.getFileManager().loadFont(12f); }
        catch (IOException | FontFormatException e) { return new Font(Font.MONOSPACED,Font.PLAIN,12); }
    }
    private static final Color[] SPARKLES = {Color.WHITE, Color.YELLOW, Color.ORANGE, Color.CYAN};
    private static final Controls controls = new Controls();
    private static engine.Frame boundFrame;
    private static Screen menuScreen;
    private static int menuIndex;
    private static boolean gateOpen, gateAvailable;
    private static final TestPassword testPassword = new TestPassword();
    private static final int GATE_WIDTH = 400, GATE_HEIGHT = 386;
    private final Run run;
    private final Records records;
    private final Ship player;
    private final GameplaySession systems;
    private final Map<Run.Body, Entity> images = new HashMap<Run.Body, Entity>();
    private final List<EnemyShip> fading = new ArrayList<EnemyShip>();
    private final List<Particle> particles = new ArrayList<Particle>();
    private final List<Hit> hits = new ArrayList<Hit>();
    private final ScreenShake shake = new ScreenShake();
    private final DamageDimEffect dim = new DamageDimEffect(900, .75f, new Color(150,0,0));
    private final GlitchEffect glitch = new GlitchEffect();
    private final boolean testing;
    private long previousTime;
    private double deathTime;
    private double lowHealthTime;
    private double rewardInputDelay;
    private boolean saved;
    private int pauseSelection;
    private String achievementNotice="";
    private long achievementUntil;

    private EndlessMode(int width, int height, boolean testing) {
        super(width, height, 60);
        this.testing = testing;
        records = new Records(Paths.get(testing ? "endless-test-records.properties" : "endless-records.properties"));
        Random random = new Random();
        systems = GameplaySession.open(testing, random);
        player = systems.getShip();
        run = new Run(width,height,random,systems);
        run.defeated = this::destroyed;
        run.enemyHit = this::hit;
        run.playerHit = () -> {
            player.destroy(); dim.trigger(run.getLives() <= 1 ? 1f : .35f);
            if(run.getLives()==1) lowHealthTime=5;
        };
    }

    /** Only reads the existing achievement. The test bypass never modifies it. */
    public static boolean unlocked() {
        for (Achievement a : Core.getAchievementManager().getAchievements())
            if ("infinity_void".equals(a.getId())) return a.isUnlocked();
        return false;
    }

    private static Object drawingField(String name) {
        try {
            Field f = DrawManager.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(null);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private static void bind() {
        engine.Frame frame = (engine.Frame)drawingField("frame");
        if (frame == boundFrame) return;
        boundFrame = frame;
        frame.addKeyListener(controls);
        frame.getContentPane().addMouseListener(controls);
        frame.getContentPane().addMouseMotionListener(controls);
    }

    private static Field menuField(String name) throws ReflectiveOperationException {
        Field field = TitleScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void prepareMenu(Screen screen) throws ReflectiveOperationException {
        bind();
        if (menuScreen == screen) return;
        menuScreen = screen;
        int ordinal = ((MenuItem)menuField("selected").get(screen)).ordinal();
        menuIndex = ordinal == 0 ? 0 : ordinal + 1;
        gateOpen = false;
        testPassword.close();
        controls.clear();
    }

    private static int menuSpacing() {
        return ((FontMetrics)drawingField("fontRegularMetrics")).getHeight() * 5 / 4;
    }

    private static int menuBaseline(Screen screen, int index) {
        // Eight rows need one extra row above the original menu to clear its hints.
        return screen.getHeight() / 2 + menuSpacing() * (index + 1);
    }

    private static int menuAt(Screen screen, Point point) {
        if (point == null || point.x < 0 || point.x >= screen.getWidth()) return -1;
        FontMetrics metrics = (FontMetrics)drawingField("fontRegularMetrics");
        int spacing = menuSpacing();
        for (int i = 0; i <= MenuItem.values().length; i++) {
            int top = menuBaseline(screen, i) - metrics.getAscent()
                    - (spacing - metrics.getHeight()) / 2;
            if (point.y >= top && point.y < top + spacing) return i;
        }
        return -1;
    }

    public static boolean drawEntry(Screen screen) throws ReflectiveOperationException {
        prepareMenu(screen);
        Graphics2D g = (Graphics2D)drawingField("backBufferGraphics");
        Font old = g.getFont(); Color oldColor = g.getColor();
        MenuItem[] items = MenuItem.values();
        for (int i = 0; i <= items.length; i++) {
            MenuItem item = i == 1 ? null : items[i == 0 ? 0 : i - 1];
            String text = item == null ? "Endless Mode" : item.getTitle();
            boolean selected = i == menuIndex;
            if (selected) text = "> " + text + " <";
            g.setFont((Font)drawingField(selected ? "fontSelected" : "fontRegular"));
            g.setColor(selected ? Color.GREEN : item != null && !item.isEnabled()
                    ? Color.DARK_GRAY : Color.WHITE);
            g.drawString(text, (screen.getWidth()-g.getFontMetrics().stringWidth(text))/2,
                    menuBaseline(screen, i));
        }
        if ((Boolean)menuField("showingExitConfirm").get(screen)) controls.clear();
        if (gateOpen) drawGate(screen, g);
        g.setFont(old); g.setColor(oldColor);
        return true;
    }

    public static boolean updateEntry(Screen screen) throws Exception {
        prepareMenu(screen);
        Point click = controls.takeClick();
        boolean escape = controls.take(KeyEvent.VK_ESCAPE);
        boolean enter = controls.take(KeyEvent.VK_ENTER) | controls.take(KeyEvent.VK_SPACE);
        if (gateOpen) {
            Point local = click == null ? null : new Point(
                    click.x - (screen.getWidth()-GATE_WIDTH)/2,
                    click.y - (screen.getHeight()-GATE_HEIGHT)/2);
            if (testPassword.open) {
                if (escape || inside(local,362,10,24,24) || inside(local,22,320,148,36)) {
                    testPassword.close();
                    controls.clear();
                } else if (testPassword.accept(controls.takePasswordInput(),
                        enter || inside(local,182,320,196,36))) {
                    startEndless(screen, true);
                }
                return true;
            }
            if (escape || inside(local, 362,10,24,24)
                    || inside(local,22,320,gateAvailable ? 148 : 300,36)) {
                gateOpen = false;
                controls.clear();
            } else if (inside(local,344,320,34,36)) {
                testPassword.show();
                controls.beginPasswordInput();
            } else if (gateAvailable && (enter || inside(local,182,320,140,36))) {
                startEndless(screen, false);
            }
            return true;
        }
        if (escape) return false; // Let the original menu show its exit confirmation.
        int hovered = menuAt(screen, controls.takeHover());
        if (hovered >= 0) menuIndex = hovered;
        if (controls.take(KeyEvent.VK_UP) | controls.take(KeyEvent.VK_W))
            menuIndex = Math.floorMod(menuIndex-1, MenuItem.values().length+1);
        if (controls.take(KeyEvent.VK_DOWN) | controls.take(KeyEvent.VK_S))
            menuIndex = (menuIndex+1) % (MenuItem.values().length+1);
        int clicked = menuAt(screen, click);
        if (clicked >= 0) menuIndex = clicked;
        boolean shortcut = controls.take(KeyEvent.VK_E);
        if (shortcut) menuIndex = 1;
        if (menuIndex != 1)
            menuField("selected").set(screen, MenuItem.values()[menuIndex == 0 ? 0 : menuIndex-1]);
        if (enter || clicked >= 0 || shortcut) {
            if (menuIndex == 1) {
                gateAvailable = unlocked();
                gateOpen = true;
                testPassword.close();
                controls.clear();
            } else {
                java.lang.reflect.Method confirm = TitleScreen.class.getDeclaredMethod("confirm");
                confirm.setAccessible(true);
                confirm.invoke(screen);
            }
        }
        return true;
    }

    private static void startEndless(Screen screen, boolean testing) throws ReflectiveOperationException {
        gateOpen = false;
        testPassword.close();
        controls.clear();
        boolean again;
        do {
            EndlessMode game = new EndlessMode(screen.getWidth(), screen.getHeight(), testing);
            again = boundFrame.setScreen(game) == 8;
            controls.clear();
        } while (again);
        menuField("escapeWasDown").set(screen, Core.getInputManager().isKeyDown(KeyEvent.VK_ESCAPE));
    }

    private static boolean inside(Point point, int x, int y, int w, int h) {
        return point != null && point.x >= x && point.x < x+w && point.y >= y && point.y < y+h;
    }

    private static void gateButton(Graphics2D g, Point pointer, String label, int x, int width) {
        boolean hover = inside(pointer, x,320,width,36);
        g.setColor(hover ? new Color(8,40,16) : new Color(0,16,5));
        g.fillRect(x,320,width,36);
        g.setColor(hover ? Color.GREEN : new Color(45,103,58));
        g.drawRect(x,320,width-1,35);
        g.setFont(UI_FONT);g.setColor(Color.GREEN);
        g.drawString(label,x+(width-g.getFontMetrics().stringWidth(label))/2,344);
    }

    private static void centerGate(Graphics2D g, String text, int y, Color color) {
        g.setColor(color);
        g.drawString(text,(GATE_WIDTH-g.getFontMetrics().stringWidth(text))/2,y);
    }

    private static void drawGate(Screen screen, Graphics2D graphics) {
        graphics.setColor(new Color(0,0,0,210));
        graphics.fillRect(0,0,screen.getWidth(),screen.getHeight());
        Graphics2D g = (Graphics2D)graphics.create();
        int left = (screen.getWidth()-GATE_WIDTH)/2, top = (screen.getHeight()-GATE_HEIGHT)/2;
        g.translate(left,top);
        Point pointer = controls.pointer();
        if (pointer != null) pointer.translate(-left,-top);
        g.setColor(Color.BLACK);g.fillRect(0,0,GATE_WIDTH,GATE_HEIGHT);
        g.setColor(new Color(0,9,3));
        for(int y=4;y<GATE_HEIGHT;y+=4)g.drawLine(1,y,GATE_WIDTH-2,y);
        g.setColor(new Color(40,115,54));g.drawRect(0,0,GATE_WIDTH-1,GATE_HEIGHT-1);
        g.setColor(Color.GREEN);
        for(int x:new int[]{0,GATE_WIDTH-17})for(int y:new int[]{0,GATE_HEIGHT-17})g.drawRect(x,y,16,16);
        g.setFont(UI_FONT.deriveFont(10f));g.setColor(MUTED);g.drawString("INVADERS / SURVIVAL",22,28);
        g.setColor(inside(pointer,362,10,24,24) ? Color.GREEN : MUTED);
        g.drawRect(362,10,23,23);
        g.drawLine(369,17,378,26);g.drawLine(378,17,369,26);
        if (testPassword.open) {
            drawTestPassword(g,pointer);
            g.dispose();
            return;
        }
        g.setFont(UI_FONT.deriveFont(24f));centerGate(g,"ENDLESS",73,Color.GREEN);
        g.setColor(gateAvailable?Color.GREEN:new Color(110,132,116));pixelLock(g,182,95,4);
        g.setFont(UI_FONT.deriveFont(14f));centerGate(g,gateAvailable?"ACCESS GRANTED":"MODE LOCKED",163,gateAvailable?Color.GREEN:Color.WHITE);
        g.setFont(UI_FONT);centerGate(g,gateAvailable?"STAGE 10 COMPLETE. KEEP FIGHTING.":"CLEAR STAGE 10 TO UNLOCK.",190,MUTED);
        g.setColor(new Color(35,76,43));g.drawLine(22,210,378,210);
        String[] labels={"SURVIVE","UPGRADE","BOSS"};String[] values={"NO LIMIT","3 WAVES","5 WAVES"};
        for(int i=0;i<3;i++){int x=22+i*122;g.setColor(new Color(22,48,28));g.drawRect(x,224,112,58);
            g.setFont(UI_FONT.deriveFont(10f));g.setColor(MUTED);g.drawString(labels[i],x+10,244);
            g.setFont(UI_FONT);g.setColor(Color.WHITE);g.drawString(values[i],x+10,268);}
        gateButton(g,pointer,"BACK",22,gateAvailable?148:300);
        if(gateAvailable)gateButton(g,pointer,"START RUN",182,140);
        gateButton(g,pointer,"",344,34);
        g.setColor(new Color(185,174,96));pixelLock(g,352,328,2);
        g.setFont(UI_FONT.deriveFont(9f));g.setColor(MUTED);g.drawString("QA",352,310);
        centerGate(g,"ESC TO RETURN",374,MUTED);
        g.dispose();
    }

    private static void drawTestPassword(Graphics2D g, Point pointer) {
        g.setFont(UI_FONT.deriveFont(22f));centerGate(g,"TEST ACCESS",83,Color.GREEN);
        g.setFont(UI_FONT);centerGate(g,"ENTER TEST PASSWORD",137,Color.WHITE);
        centerGate(g,"TYPE DIGITS, THEN PRESS ENTER",164,MUTED);
        g.setColor(new Color(4,25,13));g.fillRect(90,190,220,50);
        g.setColor(Color.GREEN);g.drawRect(90,190,220,50);
        g.setFont(UI_FONT.deriveFont(24f));
        centerGate(g,"*".repeat(testPassword.value.length())+"_",225,Color.WHITE);
        g.setFont(UI_FONT.deriveFont(10f));
        centerGate(g,testPassword.error?"INCORRECT PASSWORD. TRY AGAIN.":"BACKSPACE TO EDIT",268,
                testPassword.error?DANGER:MUTED);
        centerGate(g,"TEST ONLY / NO SHARED PROGRESS",295,MUTED);
        gateButton(g,pointer,"BACK",22,148);
        gateButton(g,pointer,"ENTER TEST",182,196);
        centerGate(g,"ESC TO CANCEL",374,MUTED);
    }

    /** Session-only QA access. Never changes the campaign unlock achievement. */
    private static final class TestPassword {
        private boolean open, error;
        private String value="";
        void show() { open=true;error=false;value=""; }
        void close() { open=false;error=false;value=""; }
        boolean accept(String input, boolean submit) {
            if (!open) return false;
            for (char c : input.toCharArray()) {
                if (c=='\b') { if(!value.isEmpty())value=value.substring(0,value.length()-1); }
                else if(c==127)value="";
                else if(c>='0' && c<='9' && value.length()<16)value+=c;
                error=false;
            }
            if (!submit) return false;
            if ("000".equals(value)) { close();return true; }
            value="";error=true;
            return false;
        }
    }

    private static void pixelLock(Graphics g,int x,int y,int scale) {
        g.fillRect(x+2*scale,y,5*scale,scale);
        g.fillRect(x+scale,y+scale,scale,4*scale);
        g.fillRect(x+7*scale,y+scale,scale,4*scale);
        g.fillRect(x,y+4*scale,9*scale,6*scale);
        g.setColor(Color.BLACK);g.fillRect(x+4*scale,y+6*scale,scale,2*scale);
    }
    @Override public void initialize(){previousTime=System.nanoTime(); controls.clear();}
    @Override public int run(){ super.run(); return returnCode; }
    @Override protected void update() {
        long now=System.nanoTime();
        double dt=Math.min(.1,(now-previousTime)/1_000_000_000.0); previousTime=now;
        Run.Phase before=run.getPhase();
        boolean esc=controls.take(KeyEvent.VK_ESCAPE), pause=controls.take(KeyEvent.VK_P);
        boolean enter=controls.take(KeyEvent.VK_ENTER), quit=controls.take(KeyEvent.VK_Q);
        boolean shop=controls.take(KeyEvent.VK_B), customize=controls.take(KeyEvent.VK_C), settings=controls.take(KeyEvent.VK_O);
        boolean achievements=controls.take(KeyEvent.VK_G), scores=controls.take(KeyEvent.VK_H);
        // Drain every frame: a combat click or item key must never select a future reward.
        Point click=controls.takeClick();
        boolean refresh=controls.take(KeyEvent.VK_R);
        boolean[] slots=new boolean[9];
        for(int i=0;i<slots.length;i++)slots[i]=controls.take(KeyEvent.VK_1+i);
        if(before==Run.Phase.REWARD) {
            rewardInputDelay=Math.max(0,rewardInputDelay-dt);
            if(rewardInputDelay==0) {
                for(int i=0;i<3;i++)if(slots[i] || (click!=null && rewardCardBounds(i).contains(click))) {
                    run.chooseReward(i);break;
                }
                if(refresh || (click!=null && refreshBounds().contains(click)))run.refreshRewards();
            }
        } else if(before==Run.Phase.PLAYING) {
            int slotCount=run.items.getView().slots.size();
            for(int i=0;i<slotCount && i<9;i++)
                if(slots[i] || (click!=null && itemSlotBounds(i,slotCount).contains(click))) run.useItem(i);
        }
        if(before==Run.Phase.OVER && deathTime>=3) {
            if(esc){systems.leave();returnCode=1; isRunning=false;}
            if(enter){returnCode=8; isRunning=false;}
        } else if(before==Run.Phase.PAUSED && quit) run.finish("CASHED OUT");
        else if(before==Run.Phase.PAUSED) {
            boolean up=controls.take(KeyEvent.VK_UP) | controls.take(KeyEvent.VK_W);
            boolean down=controls.take(KeyEvent.VK_DOWN) | controls.take(KeyEvent.VK_S);
            if(up) pauseSelection=Math.floorMod(pauseSelection-1,3);
            if(down) pauseSelection=(pauseSelection+1)%3;
            boolean select=enter | controls.take(KeyEvent.VK_SPACE);
            if(select) {
                if(pauseSelection==0) run.togglePause();
                else if(pauseSelection==1) { run.finish("RESTARTED");returnCode=8;isRunning=false;return; }
                else run.finish("CASHED OUT");
            }
            Screen menu = null;
            if(shop && !testing) menu = new ShopScreen(width,height,60);
            else if(customize && !testing) menu = new ShipSelectScreen(width,height,60);
            else if(settings) menu = new SettingsScreen(width,height,60);
            else if(achievements) menu = new AchievementsScreen(width,height,60);
            else if(scores) menu = new EndlessRecordsScreen(width,height,testing);
            if(menu!=null) {
                boundFrame.setScreen(menu); controls.clear(); previousTime=System.nanoTime(); systems.resumeAudio();
                boundFrame.requestFocusInWindow();
            }
            if((esc || pause) && run.getPhase()==Run.Phase.PAUSED) run.togglePause();
        }
        else if(esc || pause) { pauseSelection=0;run.togglePause(); }
        if(before==run.getPhase()) {
            int move=(controls.down(KeyEvent.VK_RIGHT)||controls.down(KeyEvent.VK_D)?1:0)
                    -(controls.down(KeyEvent.VK_LEFT)||controls.down(KeyEvent.VK_A)?1:0);
            run.update(dt,move,controls.down(KeyEvent.VK_SPACE));
        }
        if(run.getPhase()==Run.Phase.REWARD && before!=Run.Phase.REWARD) {
            controls.discardPresses();
            rewardInputDelay=.30;
        }
        if(run.getPhase()==Run.Phase.OVER){
            deathTime+=dt;
            if(!saved){records.save(run); saved=true;}
        }
        if(run.getPhase()!=Run.Phase.PAUSED && run.getPhase()!=Run.Phase.REWARD) {
            lowHealthTime=Math.max(0,lowHealthTime-dt);
            player.setBlinking(run.getLives()==1 && lowHealthTime>0);
            for(Particle particle:particles) particle.update();
            particles.removeIf(Particle::isDead); fading.removeIf(EnemyShip::isFadeFinished);
            for(Hit hit:hits)hit.age+=dt;
            hits.removeIf(hit->hit.age>=.60);
            shake.update(); player.update();
            for(Entity image:images.values()) if(image instanceof EnemyShip) ((EnemyShip)image).update();
            else if(image instanceof Boss) ((Boss)image).update();
        }
        boolean playing=run.getPhase()==Run.Phase.PLAYING;
        boolean advancing=playing || run.getPhase()==Run.Phase.READY
                || run.getPhase()==Run.Phase.INTERMISSION || run.getPhase()==Run.Phase.BOSS_INTRO;
        systems.getShopEffects().frame(player,playing,advancing,run.getLives(),run.countdown,dt);
        draw();
    }

    private Entity image(Run.Body b) {
        Entity result=b.entity;
        result.setPositionX((int)b.x); result.setPositionY((int)b.y);
        images.put(b,result);
        return result;
    }

    private void destroyed(Run.Body b) {
        Entity entity=image(b);
        EnemyShip explosion=entity instanceof EnemyShip ? (EnemyShip)entity
                : new EnemyShip((int)b.x,(int)b.y,DrawManager.SpriteType.EnemyShipA1);
        if(!explosion.isDestroyed())explosion.destroy(); fading.add(explosion);
        for(int i=0;i<14;i++) particles.add(new Particle(entity.getPositionX()+entity.getWidth()/2f,
                entity.getPositionY()+entity.getHeight()/2f,SPARKLES[i%SPARKLES.length]));
        shake.trigger(); images.remove(b);
    }

    private static final class Hit {
        final double x,y,damage;
        final boolean boss;
        double age;
        Hit(Run.Body target,double damage,int width,int height) {
            x=target.x+target.width+22<width?target.x+target.width+12:target.x-12;
            y=Math.min(height-43,Math.max(142,target.y+target.height/2.0+6));
            this.damage=damage;boss=target.boss;
        }
    }

    private void hit(Run.Body target,double damage) {
        if(hits.size()>=64)hits.remove(0);
        hits.add(new Hit(target,damage,width,height));
        for(int i=0;i<4;i++)particles.add(new Particle((float)(target.x+target.width/2.0),
                (float)(target.y+target.height/2.0),i%2==0?Color.WHITE:Color.CYAN));
    }

    private static String number(double value) {
        return value==Math.rint(value)?Long.toString(Math.round(value)):
                String.format(java.util.Locale.ROOT,"%.1f",value);
    }

    private void drawHits(Graphics2D g) {
        Composite composite=g.getComposite();
        g.setFont(UI_FONT.deriveFont(12f));
        for(Hit hit:hits) {
            g.setComposite(AlphaComposite.SrcOver.derive((float)Math.min(1,(.60-hit.age)/.20)));
            String value="-"+number(hit.damage);
            int x=(int)hit.x-g.getFontMetrics().stringWidth(value)/2;
            int y=(int)(hit.y-hit.age*27);
            g.setColor(Color.BLACK);
            g.drawString(value,x-1,y);g.drawString(value,x+1,y);
            g.drawString(value,x,y-1);g.drawString(value,x,y+1);
            g.setColor(hit.boss?GOLD:Color.WHITE);g.drawString(value,x,y);
        }
        g.setComposite(composite);
    }

    private void draw() {
        drawManager.initDrawing(this);
        Graphics2D g=(Graphics2D)drawingField("backBufferGraphics");
        View view=run.items.getView();
        drawManager.setWorldOffset(shake.getOffsetX(),shake.getOffsetY());
        java.awt.Shape worldClip=g.getClip();
        g.clipRect(0,130,width,Math.max(0,height-130));
        Run.Body boss=run.getBoss();
        for(Run.Body b:run.getEnemies()) {
            if(boss!=null && !b.boss) continue;
            if(b.boss && run.getPhase()==Run.Phase.BOSS_INTRO)continue;
            Entity entity=image(b);
            if(run.getPhase()==Run.Phase.OVER) drawManager.drawEntityShrunk(entity,Math.max(0,1-deathTime/1.5));
            else if(b.hitFlash>0)drawManager.drawSprite(entity.getSpriteType(),(int)b.x,(int)b.y,b.boss?Color.WHITE:Color.CYAN);
            else drawManager.drawEntity(entity,(int)b.x,(int)b.y);
        }
        images.keySet().retainAll(run.getEnemies());
        if(run.nativeBoss!=null && run.getPhase()==Run.Phase.PLAYING)
            SharedAccess.drawBossWarnings(run.nativeBoss,drawManager,138);
        for(EnemyShip e:fading) drawManager.drawEntity(e,e.getPositionX(),e.getPositionY());
        for(Particle p:particles) drawManager.drawParticle(p);
        for(Run.Body b:run.getShots()) {
            b.entity.setPositionX((int)b.x); b.entity.setPositionY((int)b.y);
            if(run.isPlayerShot(b))systems.getShopEffects().drawBullet(drawManager,(Bullet)b.entity);
            drawManager.drawEntity(b.entity,(int)b.x,(int)b.y);
        }
        drawHits(g);
        systems.drawWorld(drawManager,g,UI_FONT);
        systems.getShopEffects().drawLightning(drawManager);
        drawManager.drawItemDrops(systems.getItems().nativeSystem());
        player.setPositionX((int)run.getPlayer().x); player.setPositionY((int)run.getPlayer().y);
        player.setSkinColor(systems.getSkin().colorAt(System.currentTimeMillis()));
        systems.getShopEffects().drawGhost(drawManager,player,player.getColor(),systems.getItems().nativeSystem());
        if(!systems.getShopEffects().hidesShip())drawManager.drawEntity(player,player.getPositionX(),player.getPositionY());
        systems.getShopEffects().drawWarp(drawManager,player,player.getColor());
        drawManager.drawItemShield(player,systems.getItems().nativeSystem());
        g.setClip(worldClip);
        drawManager.setWorldOffset(0,0); drawManager.drawDamageDim(this,dim);
        glitch.setEnabled(run.getPhase()==Run.Phase.PLAYING && run.getLives()==1 && lowHealthTime>0);
        drawManager.drawGlitch(this,glitch);
        // Keep health, timers and input hints readable even during damage/glitch effects.
        drawHud(g,view,boss);
        Run.Phase phase=run.getPhase();
        if(phase==Run.Phase.REWARD) drawRewards(g);
        else if(phase==Run.Phase.BOSS_INTRO)drawBossIntro(g,boss);
        else if(phase==Run.Phase.READY){
            panel(g,"GET READY / "+run.getCountdown());
            row(g,"A/D OR ARROWS MOVE / SPACE FIRE",0);
            row(g,"P / ESC PAUSE / Q END WHILE PAUSED",1);
            row(g,"CHOOSE AN UPGRADE EVERY 3 WAVES",2);
            row(g,"SHIP / "+player.getType().name().replace('_',' '),3);
            ItemInfo gear=systems.getItems().equippedThisRun();
            row(g,"GEAR / "+(gear==null?"NONE":gear.displayName.toUpperCase(java.util.Locale.ROOT)),4);
        } else if(phase==Run.Phase.INTERMISSION){
            panel(g,"WAVE "+run.getWave()+" IN "+run.getCountdown());
            row(g,run.getNotice().isEmpty()?"SECTOR CLEAR":run.getNotice(),0);
            row(g,"NEXT UPGRADE IN "+run.getWavesUntilReward()+" WAVE(S)",1);
        } else if(phase==Run.Phase.PAUSED){
            drawManager.drawPauseMenu(this,new String[]{"RESUME","RESTART","CASH OUT / QUIT"},pauseSelection);
            drawManager.drawKeyHints(this,"w+s / arrows to move, space / enter to select");
            fitted(g,testing?"QA / NO NORMAL REWARDS":"B SHOP / C CUSTOMIZE / O SETTINGS",12,145,width-24,10,HUD_LABEL);
            fitted(g,"G ACHIEVEMENTS / H RECORDS / Q CASH OUT",12,height-48,width-24,9,HUD_LABEL);
        } else if(phase==Run.Phase.OVER){
            if(deathTime<3) drawManager.drawGameOverBanner(this,"GAME OVER",Math.min(9,(int)(deathTime*5)+1));
            else {panel(g,run.getEndReason()); row(g,"SCORE "+run.getScore()+" / BEST "+records.getScore(),0);
                row(g,"TIME "+run.getSeconds()+"s / KILLS "+run.getKills()+" / BOSSES "+run.getBosses(),1);
                row(g,"ENTER RETRY / ESC MAIN MENU",2); row(g,records.getStatus(),3);
                row(g,"COINS +"+systems.getCoinsEarned()+" / DIAMONDS BANKED +"+systems.getBankedDiamonds(),4);}
        }
        drawManager.completeDrawing(this);
    }

    private Rectangle itemSlotBounds(int index,int count) {
        count=Math.max(1,count);
        int slotWidth=(width-16-6*(count-1))/count;
        return new Rectangle(8+index*(slotWidth+6),height-32,slotWidth,22);
    }

    private static void fitted(Graphics2D g,String value,int x,int y,int available,float size,Color color) {
        Font font=UI_FONT.deriveFont(size);g.setFont(font);
        while(size>8 && g.getFontMetrics().stringWidth(value)>available)g.setFont(font.deriveFont(--size));
        while(value.length()>3 && g.getFontMetrics().stringWidth(value)>available)
            value=value.substring(0,value.length()-4)+"...";
        g.setColor(color);g.drawString(value,x,y);
    }

    private static void bar(Graphics2D g,int x,int y,int w,int h,double amount,Color color) {
        g.setColor(new Color(13,35,25));g.fillRect(x,y,w,h);
        g.setColor(color);g.fillRect(x+1,y+1,(int)((w-2)*Math.max(0,Math.min(1,amount))),h-2);
        g.setColor(HUD_BORDER);g.drawRect(x,y,w-1,h-1);
    }

    private static void heart(Graphics2D g,int x,int y,Color color) {
        g.setColor(color);
        g.fillRect(x+2,y,4,2);g.fillRect(x+8,y,4,2);
        g.fillRect(x,y+2,14,6);g.fillRect(x+2,y+8,10,2);
        g.fillRect(x+4,y+10,6,2);g.fillRect(x+6,y+12,2,2);
    }

    private void drawHud(Graphics2D g,View view,Run.Body boss) {
        g.setColor(HUD_PANEL);g.fillRect(0,0,width,130);
        g.setColor(HUD_BORDER);g.drawLine(0,129,width,129);
        fitted(g,testing?"ENDLESS / QA":"ENDLESS",12,18,80,9,Color.CYAN);
        fitted(g,"WAVE "+run.getWave(),12,42,88,18,Color.WHITE);
        fitted(g,"SCORE / "+run.getKills()+" KILLS",108,18,width-254,9,HUD_LABEL);
        fitted(g,Long.toString(run.getScore()),108,42,width-254,19,Color.WHITE);

        int hullX=width-136;
        Color hull=run.getLives()<=1?DANGER:run.getLives()==2?GOLD:Color.GREEN;
        g.setColor(run.getLives()==1?new Color(45,8,14):new Color(4,28,17));
        g.fillRect(hullX,7,124,43);
        g.setColor(run.getLives()==1 && System.currentTimeMillis()/500%2==0?DANGER:hull);
        g.drawRect(hullX,7,123,42);
        fitted(g,run.getLives()==1?"HULL CRITICAL":"HULL / LIVES",hullX+8,20,110,9,hull);
        for(int i=0;i<Math.min(5,run.lifeCap);i++)
            heart(g,hullX+8+i*17,28,i<run.getLives()?hull:new Color(42,58,49));
        fitted(g,run.getLives()+"/"+run.lifeCap,hullX+91,42,30,12,Color.WHITE);

        fitted(g,String.format(java.util.Locale.ROOT,"TIME %02d:%02d",run.getSeconds()/60,run.getSeconds()%60),12,62,132,10,Color.WHITE);
        fitted(g,"LEFT "+run.getRemainingEnemies(),152,62,85,10,HUD_LABEL);
        fitted(g,String.format(java.util.Locale.ROOT,"DIFF %.2fX",run.getDifficulty()),width-112,62,102,10,HUD_LABEL);
        if(boss==null) {
            fitted(g,"NEXT BOSS / WAVE "+run.getNextBossWave(),12,79,width-104,10,HUD_LABEL);
            bar(g,width-86,72,74,7,((run.getWave()-1)%5)/4.0,Color.ORANGE);
        } else {
            fitted(g,"BOSS "+(int)Math.ceil(boss.hp)+" / "+(int)boss.maxHp,12,79,150,10,Color.ORANGE);
            bar(g,166,71,width-178,10,boss.hp/boss.maxHp,Color.ORANGE);
        }
        fitted(g,"UPGRADE IN "+run.getWavesUntilReward()+" WAVE(S)",12,95,width-140,10,GOLD);
        int completed=(run.getWave()-1)%3;
        for(int i=0;i<3;i++)bar(g,width-124+i*38,87,30,9,i<completed?1:0,GOLD);

        g.setFont(UI_FONT.deriveFont(8f));g.setColor(HUD_LABEL);g.drawString("BUFFS:",12,107);
        int effectX=50;
        if(view.effects.isEmpty())g.drawString("NONE",effectX,107);
        for(EffectView effect:view.effects) {
            String tag;
            switch(effect.item.effectKind) {
                case RAPID_FIRE:tag="FIRE";break;
                case BULLET_SPEED:tag="BULLET";break;
                case SCORE_BOOST:tag="SCORE X2";break;
                case SHIELD:tag="SHIELD";break;
                case FREEZE:tag="FREEZE";break;
                case BOOST:tag="BOOST";break;
                default:tag=effect.item.displayName.toUpperCase(java.util.Locale.ROOT);
            }
            if(effect.stacks!=null)tag+="x"+effect.stacks;
            if(effect.remainingMillis!=null)tag+=" "+(effect.remainingMillis+999)/1000+"s";
            int span=g.getFontMetrics().stringWidth(tag)+10;
            if(effectX+span>width-8)break;
            g.setColor(ItemSystem.colorOf(effect.item.effectKind));g.drawString(tag,effectX,107);effectX+=span;
        }

        fitted(g,"COINS "+systems.getWalletCoins()+" / +"+systems.getCoinsEarned()
                +"   DIAMONDS "+systems.getWalletDiamonds()+" / RISK "+systems.getPendingDiamonds(),
                12,120,width-24,8,GOLD);
        String unlocked=systems.pollAchievement();
        if(unlocked!=null){achievementNotice=unlocked;achievementUntil=System.currentTimeMillis()+4000;}
        if(System.currentTimeMillis()<achievementUntil) {
            g.setColor(HUD_PANEL);g.fillRect(8,134,width-16,28);
            fitted(g,"ACHIEVEMENT / "+achievementNotice,16,153,width-32,11,GOLD);
        }

        for(int i=0;i<view.slots.size();i++) {
            Rectangle slot=itemSlotBounds(i,view.slots.size());ItemInfo item=view.slots.get(i);
            Color color=item==null?HUD_LABEL:ItemSystem.colorOf(item.effectKind);
            g.setColor(HUD_PANEL);g.fillRect(slot.x,slot.y,slot.width,slot.height);
            g.setColor(item==null?HUD_BORDER:color);g.drawRect(slot.x,slot.y,slot.width-1,slot.height-1);
            g.drawRect(slot.x+3,slot.y+3,14,14);
            fitted(g,Integer.toString(i+1),slot.x+7,slot.y+14,12,9,color);
            fitted(g,item==null?"EMPTY":item.displayName.toUpperCase(java.util.Locale.ROOT),slot.x+24,slot.y+14,slot.width-29,10,color);
        }
        Modifiers modifiers=run.items.getModifiers();
        String stats=String.format(java.util.Locale.ROOT,"DMG %.1fX  FIRE %.1f/S  SPEED %d  SHOTS %d",run.damage,
                run.fireRate*modifiers.fireRateMultiplier+modifiers.fireRateBonus,
                Math.round(run.playerSpeed*modifiers.movementSpeedMultiplier),run.getProjectiles());
        g.setColor(Color.BLACK);g.fillRect(0,height-9,width,9);
        fitted(g,stats,9,height-2,width-18,8,HUD_LABEL);
        if(!run.getNotice().isEmpty()) {
            g.setFont(UI_FONT);int w=Math.min(width-32,g.getFontMetrics().stringWidth(run.getNotice())+20);
            g.setColor(HUD_PANEL);g.fillRect((width-w)/2,height-94,w,22);
            fitted(g,run.getNotice(),(width-w)/2+10,height-78,w-20,12,GOLD);
        }
    }

    private void panel(Graphics2D g,String title){drawManager.drawFadeOverlay(this,225); text(g,title,24,height/2-60,Color.GREEN);}
    private Rectangle rewardCardBounds(int index) {
        int cardHeight=Math.min(80,(height-224)/3);
        return new Rectangle(18,114+index*(cardHeight+10),width-36,cardHeight);
    }
    private Rectangle refreshBounds(){return new Rectangle(18,height-78,width-36,34);}
    private void centered(Graphics2D g,String value,int y,float size,Color color) {
        g.setFont(UI_FONT.deriveFont(size));g.setColor(color);
        g.drawString(value,(width-g.getFontMetrics().stringWidth(value))/2,y);
    }
    private void drawBossIntro(Graphics2D g,Run.Body boss) {
        if(boss==null)return;
        drawManager.drawFadeOverlay(this,225);
        int top=Math.max(122,height/2-122)+(int)(Math.max(0,run.countdown-2.5)*24);
        g.setColor(HUD_PANEL);g.fillRect(12,top,width-24,283);
        g.setColor(Color.ORANGE);g.drawRect(12,top,width-25,282);
        g.fillRect(12,top,width-24,4);g.fillRect(12,top+278,width-24,4);
        centered(g,"WARNING / WAVE "+run.getWave(),top+25,10,GOLD);
        centered(g,"BOSS INCOMING",top+58,22,Color.ORANGE);
        centered(g,SharedAccess.bossName((Boss)boss.entity).toUpperCase(java.util.Locale.ROOT),top+79,11,Color.WHITE);
        int portraitScale=Math.max(1,Math.min(4,56/Math.max(1,boss.height/2)));
        drawManager.drawSpriteScaled(boss.entity.getSpriteType(),width/2-boss.width*portraitScale/4,
                top+87,portraitScale,boss.entity.getColor());
        int statWidth=(width-60)/2;
        for(int i=0;i<2;i++) {
            int x=24+i*(statWidth+12);
            g.setColor(HUD_BORDER);g.drawRect(x,top+151,statWidth,58);
            fitted(g,i==0?"MAX HP":"DAMAGE / HIT",x+12,top+170,statWidth-24,10,HUD_LABEL);
            fitted(g,i==0?number(boss.maxHp):number(boss.damage)+" LIFE",x+12,top+198,statWidth-24,20,i==0?GOLD:DANGER);
        }
        centered(g,"BATTLE STARTS IN "+run.getCountdown(),top+238,13,Color.WHITE);
        bar(g,32,top+247,width-64,7,1-run.countdown/3.0,Color.ORANGE);
        centered(g,"FOLLOW ITS MOVEMENT / DODGE THE SHOTS",top+270,9,HUD_LABEL);
    }
    private void drawRewards(Graphics2D g) {
        drawManager.drawFadeOverlay(this,245);
        g.setColor(HUD_BORDER);g.drawRect(8,18,width-17,height-28);
        centered(g,"WAVE UPGRADE",53,22,Color.GREEN);
        centered(g,"WAVE "+(run.getWave()-1)+" CLEARED",78,12,Color.WHITE);
        centered(g,"CHOOSE ONE / NEXT WAVE "+run.getWave(),97,10,HUD_LABEL);
        Point pointer=controls.pointer();
        for(int i=0;i<run.offers.size();i++) {
            Run.Reward offer=run.offers.get(i);Rectangle card=rewardCardBounds(i);
            boolean hover=pointer!=null && card.contains(pointer);
            Color accent=offer.itemId==null?Color.GREEN:Color.CYAN;
            g.setColor(hover?new Color(8,40,26):HUD_PANEL);g.fillRect(card.x,card.y,card.width,card.height);
            g.setColor(hover?accent:HUD_BORDER);g.drawRect(card.x,card.y,card.width-1,card.height-1);
            if(hover)g.drawRect(card.x+1,card.y+1,card.width-3,card.height-3);
            g.setColor(accent);g.drawRect(card.x+10,card.y+25,25,25);
            fitted(g,Integer.toString(i+1),card.x+18,card.y+44,20,16,accent);
            fitted(g,offer.itemId==null?"PERMANENT UPGRADE":"SUPPLY / ITEM",card.x+48,card.y+18,card.width-58,9,accent);
            fitted(g,offer.title,card.x+48,card.y+40,card.width-58,15,Color.WHITE);
            wrapped(g,offer.description,card.x+48,card.y+57,card.width-58,HUD_LABEL);
        }
        Rectangle refresh=refreshBounds();
        g.setColor(run.refreshed?HUD_PANEL:new Color(39,32,8));g.fillRect(refresh.x,refresh.y,refresh.width,refresh.height);
        g.setColor(run.refreshed?HUD_BORDER:GOLD);g.drawRect(refresh.x,refresh.y,refresh.width-1,refresh.height-1);
        centered(g,run.refreshed?"REFRESH USED":"[R] REFRESH OPTIONS / 1 LEFT",refresh.y+22,12,run.refreshed?HUD_LABEL:GOLD);
        centered(g,rewardInputDelay>0?"RELEASE KEYS, THEN CHOOSE":"CLICK A CARD OR PRESS 1 / 2 / 3",height-25,10,Color.WHITE);
    }
    private void row(Graphics2D g,String text,int index){text(g,text,24,height/2-20+index*27,Color.WHITE);}
    private static void wrapped(Graphics2D g,String value,int x,int y,int width,Color color) {
        g.setFont(UI_FONT.deriveFont(8f));g.setColor(color);
        String[] words=value.split(" ");int word=0;
        for(int line=0;line<2 && word<words.length;line++) {
            String text="";
            while(word<words.length && g.getFontMetrics().stringWidth(text+" "+words[word])<=width)
                text+=(text.isEmpty()?"":" ")+words[word++];
            if(text.isEmpty())text=words[word++];
            if(line==1 && word<words.length) {
                while(!text.isEmpty() && g.getFontMetrics().stringWidth(text+"...")>width)text=text.substring(0,text.length()-1);
                text+="...";
            }
            g.drawString(text,x,y+line*12);
        }
    }
    private static void text(Graphics2D g,String text,int x,int y,Color color){g.setFont(UI_FONT);g.setColor(color);g.drawString(text,x,y);}

    /** Endless-owned view of its records; the game's original high-score page is unchanged. */
    private static final class EndlessRecordsScreen extends Screen {
        private final Records best;
        private final boolean testing;
        EndlessRecordsScreen(int width,int height,boolean testing){
            super(width,height,60);this.testing=testing;
            best=new Records(Paths.get(testing?"endless-test-records.properties":"endless-records.properties"));
        }
        @Override public int run(){super.run();return 1;}
        @Override protected void update(){
            drawManager.initDrawing(this);
            drawManager.drawScreenTitle(this,"ENDLESS RECORDS");
            drawManager.drawCenteredRegularString(this,testing?"QA PERSONAL BESTS":"PERSONAL BESTS",height/3);
            drawManager.drawCenteredRegularString(this,"SCORE "+best.getScore(),height/2-20);
            drawManager.drawCenteredRegularString(this,"SURVIVED "+best.getSeconds()+"s",height/2+12);
            drawManager.drawCenteredRegularString(this,"ENEMIES "+best.getKills(),height/2+44);
            drawManager.drawKeyHints(this,"esc back");drawManager.completeDrawing(this);
            if(inputManager.isKeyDown(KeyEvent.VK_ESCAPE) && inputDelay.checkFinished())isRunning=false;
        }
    }

    private static final class Controls extends MouseAdapter implements KeyListener {
        private final boolean[] down=new boolean[256], pressed=new boolean[256];
        private Point click, hover;
        private boolean moved;
        private boolean passwordInputActive;
        private final StringBuilder passwordInput = new StringBuilder();
        @Override public synchronized void keyPressed(KeyEvent e){
            int k=e.getKeyCode();
            if(k>=0&&k<256){
                if(!down[k]){
                    pressed[k]=true;
                    if(passwordInputActive){
                        if(k>=KeyEvent.VK_0 && k<=KeyEvent.VK_9)passwordInput.append((char)('0'+k-KeyEvent.VK_0));
                        else if(k>=KeyEvent.VK_NUMPAD0 && k<=KeyEvent.VK_NUMPAD9)passwordInput.append((char)('0'+k-KeyEvent.VK_NUMPAD0));
                        else if(k==KeyEvent.VK_BACK_SPACE)passwordInput.append('\b');
                        else if(k==KeyEvent.VK_DELETE)passwordInput.append((char)127);
                    }
                }
                down[k]=true;
            }
        }
        @Override public synchronized void keyReleased(KeyEvent e){int k=e.getKeyCode();if(k>=0&&k<256)down[k]=false;}
        @Override public void keyTyped(KeyEvent e){}
        synchronized boolean take(int k){boolean p=pressed[k];pressed[k]=false;return p;}
        synchronized boolean down(int k){return down[k];}
        synchronized Point takeClick(){Point p=click;click=null;return p;}
        synchronized Point takeHover(){Point p=moved?hover:null;moved=false;return p;}
        synchronized Point pointer(){return hover==null?null:new Point(hover);}
        synchronized void discardPresses(){java.util.Arrays.fill(pressed,false);click=null;}
        synchronized void clear(){discardPresses();java.util.Arrays.fill(down,false);moved=false;passwordInputActive=false;passwordInput.setLength(0);}
        synchronized void beginPasswordInput(){clear();passwordInputActive=true;}
        synchronized String takePasswordInput(){String input=passwordInput.toString();passwordInput.setLength(0);return input;}
        @Override public synchronized void mousePressed(MouseEvent e){if(e.getButton()==MouseEvent.BUTTON1)click=e.getPoint();}
        @Override public synchronized void mouseMoved(MouseEvent e){hover=e.getPoint();moved=true;}
    }

    public static final class Run {
    public enum Phase { READY, BOSS_INTRO, PLAYING, INTERMISSION, REWARD, PAUSED, OVER }

    public static final class Reward {
        public final String id, title, description, itemId;
        Reward(String id,String title,String description,String itemId){this.id=id;this.title=title;this.description=description;this.itemId=itemId;}
    }

    /** Geometry shared with rendering; positions use sub-pixel precision. */
    public static final class Body {
        public double x, y;
        public final int width, height;
        public final boolean boss;
        public double hp, maxHp;
        private double vx, vy, damage, hitFlash;
        public Entity entity;

        private Body(double x, double y, int width, int height, boolean boss) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.boss = boss;
            hp = maxHp = 1;
        }
    }

    private final int width, height;
    private final Random random;
    private final List<Body> enemies = new ArrayList<Body>();
    private final List<Body> shots = new ArrayList<Body>();
    private final Body player;
    private final GameplaySession systems;
    private final SharedAccess.Items itemSystem;
    private final Ship ship;
    private Boss nativeBoss;
    private double bossMoveRemainder, bossHealthFloor;
    private long bossPausedAt;
    private int bulletsFired;
    public final ItemAPI items;
    private List<Reward> offers=Collections.emptyList();
    private boolean refreshed;
    private int lifeCap=3,lifeBonus=500;
    private double itemTime, fireRate=1/.75, timeBonus;
    private long bonusPoints;
    private Phase phase = Phase.READY, resumePhase;
    private double elapsed, countdown = 3, shotClock, enemyShotClock;
    private double invulnerability, moveRemainder, noticeClock;
    private int kills, bosses, wave = 1, lives = 3, projectiles = 1;
    private int direction = 1, damageUpgrades, speedUpgrades;
    private double damage = 1, playerSpeed = 120;
    private String notice = "", endReason = "";
    private java.util.function.Consumer<Body> defeated = b -> {};
    private java.util.function.BiConsumer<Body,Double> enemyHit = (b,amount) -> {};
    private Runnable playerHit = () -> {};

    public Run(int width, int height, Random random) {
        this(width,height,random,GameplaySession.isolated(Ship.ShipType.STANDARD,Skin.CLASSIC,null,random));
    }

    public Run(int width, int height, Random random, GameplaySession systems) {
        this.width = width;
        this.height = height;
        this.random = random;
        this.systems = systems;
        ship=systems.getShip();
        itemSystem=systems.getItems();
        items=itemSystem.api();
        lifeCap=itemSystem.getMaxLives();
        lives=itemSystem.getStartingLives();
        lifeBonus=itemSystem.getLifeCapBonusScore();
        playerSpeed=ship.getBaseSpeed()*60.0;
        fireRate=ship.getBaseFireRate();
        projectiles=ship.getType().getBulletsPerShot();
        player = new Body(width / 2.0 - ship.getWidth()/2.0, height - 52, ship.getWidth(),ship.getHeight(),false);
        ship.setPositionX((int)player.x);ship.setPositionY((int)player.y);
        itemSystem.beginRun("endless",width,130,player.y+player.height,new ItemSystem.GameHooks(){
            public int getLives(){return lives;}
            public void addLife(){lives++;}
            public void addScore(int points){bonusPoints+=points;}
        });
        systems.start(width,height);
        spawnWave();
    }

    /** Bounded fixed steps keep collision detection stable even after a slow frame. */
    public void update(double seconds, int movement, boolean fire) {
        if (!Double.isFinite(seconds) || seconds <= 0) return;
        double remaining = Math.min(seconds, 0.1);
        while (remaining > 0.000001) {
            double step = Math.min(remaining, 1.0 / 120);
            tick(step, Math.max(-1, Math.min(1, movement)), fire);
            remaining -= step;
        }
    }

    private void tick(double dt, int movement, boolean fire) {
        if (phase == Phase.PAUSED || phase == Phase.REWARD || phase == Phase.OVER) return;
        if (phase == Phase.READY || phase == Phase.INTERMISSION || phase == Phase.BOSS_INTRO) {
            countdown -= dt;
            if (countdown <= 0) phase = Phase.PLAYING;
            return;
        }
        elapsed += dt;
        itemTime+=dt*1000;
        long milliseconds=(long)itemTime;itemTime-=milliseconds;
        itemSystem.advance(milliseconds,new PlayerSnapshot(new Bounds(player.x,player.y,player.width,player.height),invulnerability<=0,true));
        itemEvents();
        Modifiers mods=items.getModifiers();
        timeBonus+=dt*10*(mods.scoreMultiplier-1);
        for(Body enemy:enemies)enemy.hitFlash=Math.max(0,enemy.hitFlash-dt);
        invulnerability = Math.max(0, invulnerability - dt);
        noticeClock = Math.max(0, noticeClock - dt);
        player.x = Math.max(8, Math.min(width - player.width - 8,
                player.x + movement * playerSpeed * mods.movementSpeedMultiplier * dt));
        shotClock -= dt;
        if (fire && shotClock <= 0) {
            ship.setPositionX((int)player.x);ship.setPositionY((int)player.y);
            ship.setItemBonuses(mods.fireRateBonus,mods.bulletSpeedBonus);
            List<Bullet> volley=SharedAccess.volley(ship);
            for(int i=0;i<projectiles;i++) {
                Bullet model=volley.get(i%volley.size());
                Body shot=new Body(model.getPositionX()+(i/volley.size())*10,
                        model.getPositionY(),model.getWidth(),model.getHeight(),false);
                shot.vy=model.getSpeed()*60.0;shot.damage=damage;shot.entity=model;
                shots.add(shot);
            }
            bulletsFired+=projectiles;systems.shot(projectiles);
            shotClock = 1/(fireRate*mods.fireRateMultiplier+mods.fireRateBonus);
        }
        ship.setPositionX((int)player.x);ship.setPositionY((int)player.y);
        if(!mods.enemyMovementBlocked)moveEnemies(dt);
        enemyShotClock -= dt;
        if (enemyShotClock <= 0 && !enemies.isEmpty()) {
            fireEnemyShots();
            enemyShotClock = nativeBoss==null ? Math.max(0.32, 1.35 / getDifficulty())
                    : Math.max(.25,SharedAccess.bossInterval(nativeBoss)/1000.0);
        }
        updateShots(dt);
        if (phase == Phase.OVER) return;
        List<Entity> models=new ArrayList<Entity>();
        for(Body enemy:enemies){enemy.entity.setPositionX((int)enemy.x);enemy.entity.setPositionY((int)enemy.y);models.add(enemy.entity);}
        systems.update(dt,models,invulnerability<=0);
        for(Body enemy:enemies){enemy.x=enemy.entity.getPositionX();enemy.y=enemy.entity.getPositionY();}
        for (Body enemy : enemies) {
            if (!enemy.boss && enemy.y + enemy.height >= player.y) {
                lives = 0;
                finish("FORMATION BREACHED");
                return;
            }
        }
        if (enemies.isEmpty()) {
            systems.waveCleared();
            wave++;
            shots.clear();
            phase = Phase.INTERMISSION;
            countdown = 2;
            spawnWave();
            if ((wave - 1) % 3 == 0 && lives < lifeCap) {
                lives++;
                notice = "REPAIR: +1 LIFE";
                noticeClock = 5;
            }
            if((wave-1)%3==0){phase=Phase.REWARD;refreshed=false;rollRewards();}
        }
    }

    private void spawnWave() {
        direction=1;enemyShotClock=1.2;moveRemainder=0;invulnerability=1.5;
        nativeBoss=null;bossMoveRemainder=0;systems.beginWave(wave,wave%5==0);
        if(wave%5==0){spawnBoss();return;}
        int count = 12 + Math.min(23, (wave - 1) * 2);
        int columns = Math.min(7, 4 + (wave - 1) / 3);
        double left = (width - columns * 40) / 2.0;
        for (int i = 0; i < count; i++) {
            Entity model=systems.createEnemy(wave,i,(int)(left+(i%columns)*40),138+(i/columns)*29);
            Body enemy = new Body(model.getPositionX(),model.getPositionY(),model.getWidth(),model.getHeight(),false);
            enemy.entity=model;
            // Health keeps progressing after speed/density reach playable limits.
            enemy.hp = enemy.maxHp = systems.enemyHealth(model,1 + Math.floor(elapsed / 180) * 0.5);
            enemies.add(enemy);
        }
    }

    private void spawnBoss() {
        nativeBoss=systems.createBoss(wave,width/2,138);
        Entity model=(Entity)nativeBoss;
        Body boss = new Body(model.getPositionX(),model.getPositionY(),model.getWidth(),model.getHeight(),true);
        boss.entity=model;
        bossHealthFloor=Math.max(bossHealthFloor,SharedAccess.bossHealth(nativeBoss));
        boss.hp = boss.maxHp = bossHealthFloor*9.0 + bosses * 8.0 + Math.floor(elapsed / 90) * 2;
        boss.damage = SharedAccess.bossDamage(nativeBoss);
        enemies.add(boss);
        shots.clear();
        enemyShotClock = 1;
        phase = Phase.BOSS_INTRO;
        countdown = 3;
        notice = "BOSS INCOMING";
        noticeClock = 2;
    }

    private void moveEnemies(double dt) {
        Body boss = getBoss();
        if (boss != null) {
            bossMoveRemainder+=dt*60;
            GameSettings settings=bossSettings();
            while(bossMoveRemainder>=1) {
                bossMoveRemainder--;
                SharedAccess.moveBoss(nativeBoss,width,height,138,settings);
            }
            boss.x=nativeBoss.getPositionX();boss.y=nativeBoss.getPositionY();
            return;
        }
        moveRemainder += dt;
        double interval = Math.max(0.08, 0.50 / getDifficulty());
        if (moveRemainder < interval) return;
        moveRemainder -= interval;
        boolean turn = false;
        for (Body enemy : enemies)
            if (enemy.x + direction * 8 < 12
                    || enemy.x + enemy.width + direction * 8 > width - 12) turn = true;
        if (turn) direction = -direction;
        for (Body enemy : enemies) {
            enemy.x += direction * 8;
            if (turn) enemy.y += 12;
        }
    }

    private void fireEnemyShots() {
        Body boss = getBoss();
        if (boss != null) {
            java.util.Set<Bullet> volley=new java.util.LinkedHashSet<Bullet>();
            nativeBoss.setTarget((int)(player.x+player.width/2),(int)(player.y+player.height/2));
            SharedAccess.bossVolley(nativeBoss,volley,bossSettings());
            for(Bullet bullet:volley) {
                if(nativeBoss instanceof FinalBoss && bullet.getPositionY()==((Number)SharedAccess.get(FinalBoss.class,"RAIN_START_Y")).intValue())
                    bullet.setPositionY(bullet.getPositionY()+138-((Number)SharedAccess.get(FinalBoss.class,"TOP_BOUNDARY")).intValue());
                Body shot=new Body(bullet.getPositionX(),bullet.getPositionY(),bullet.getWidth(),bullet.getHeight(),false);
                shot.entity=bullet;shot.vx=bullet.getSpeedX()*60.0;shot.vy=bullet.getSpeed()*60.0;shot.damage=boss.damage;shots.add(shot);
            }
        } else {
            Body enemy = enemies.get(random.nextInt(enemies.size()));
            addEnemyShot(enemy, 0, Math.min(240, 135 * getDifficulty()));
        }
    }

    private GameSettings bossSettings(){return new GameSettings(1,1,
            Math.max(1,(int)Math.round(2*getDifficulty())),(int)Math.max(500,1350/getDifficulty()));}

    private void addEnemyShot(Body source, double vx, double vy) {
        Body shot = new Body(source.x + source.width / 2.0 - 3,
                source.y + source.height, 6, 10, false);
        shot.entity=new Bullet((int)shot.x,(int)shot.y,(int)Math.round(vy/60));
        shot.vx = vx;
        shot.vy = vy;
        shot.damage=source.boss?source.damage:1;
        shots.add(shot);
    }

    private void updateShots(double dt) {
        Iterator<Body> iterator = shots.iterator();
        while (iterator.hasNext()) {
            Body shot = iterator.next();
            shot.x += shot.vx * dt;
            shot.y += shot.vy * dt;
            boolean remove = shot.y < 130 || shot.y > height || shot.x < -10 || shot.x > width;
            if (shot.vy < 0) {
                shot.entity.setPositionX((int)shot.x);shot.entity.setPositionY((int)shot.y);
                if(systems.interceptPlayerBullet((Bullet)shot.entity)){iterator.remove();continue;}
                Body boss = getBoss();
                Iterator<Body> targets = enemies.iterator();
                while (targets.hasNext()) {
                    Body target = targets.next();
                    if (boss != null && !target.boss) continue;
                    if (!overlaps(shot, target)) continue;
                    target.entity.setPositionX((int)target.x);target.entity.setPositionY((int)target.y);
                    target.hp -= shot.damage;
                    target.hitFlash=.12;
                    enemyHit.accept(target,shot.damage);systems.enemyHit(target.entity);
                    remove = true;
                    if (target.hp <= 0.00001) {
                        bonusPoints+=Math.round(100*(items.getModifiers().scoreMultiplier-1));
                        items.onEnemyDefeated(target.boss?DropSource.SPECIAL_ENEMY:DropSource.REGULAR_ENEMY,
                                target.x+target.width/2.0,target.y+target.height/2.0);
                        systems.enemyDefeated(target.entity,target.boss,(int)Math.round(100*items.getModifiers().scoreMultiplier));
                        defeated.accept(target); targets.remove();
                        kills++;
                        if (target.boss) {
                            bosses++;
                        }
                    }
                    break; // One projectile can damage exactly one enemy.
                }
            } else if (overlaps(shot, player)) {
                remove = true;
                if (invulnerability <= 0) {
                    if(items.tryBlockHit()){iterator.remove();continue;}
                    lives=Math.max(0,lives-(int)Math.ceil(Math.max(1,shot.damage)));systems.playerHit(lives);playerHit.run();
                    invulnerability = 1.5;
                    if (lives == 0) {
                        finish("SHIP LOST");
                        return;
                    }
                }
            }
            if (remove) iterator.remove();
        }
    }

    public void useItem(int slot){if(phase==Phase.PLAYING){UseResult result=itemSystem.useSlot(slot);if(result!=UseResult.USED)notice="ITEM: "+result;noticeClock=3;itemEvents();}}
    private void itemEvents(){
        for(ItemAPI.ItemEvent event:items.drainEvents()){
            systems.itemEvent(event);
            if(event.type==EventType.ITEM_COLLECTED||event.type==EventType.ITEM_GRANTED||event.type==EventType.EFFECT_STARTED){
                ItemInfo info=items.getItemInfo(event.itemId);notice=info.displayName.toUpperCase(java.util.Locale.ROOT);noticeClock=3;
            }else if(event.type==EventType.SHIELD_BLOCKED){notice="SHIELD BLOCKED A HIT";noticeClock=3;}
        }
    }
    private GrantRequest request(String id){return new GrantRequest("wave-"+(wave-1)+"-"+id,id,GrantSource.REWARD,GrantTiming.NOW);}
    private void rollRewards(){
        List<Reward> perks=new ArrayList<Reward>();
        perks.add(new Reward("damage","DAMAGE +10%","PERMANENT ATTACK UPGRADE",null));
        perks.add(new Reward("fire","FIRE RATE +10%","PERMANENT ATTACK SPEED UPGRADE",null));
        if(playerSpeed<360)perks.add(new Reward("speed","MOVE SPEED +10%","PERMANENT MOVEMENT UPGRADE",null));
        if(projectiles<5)perks.add(new Reward("projectile","+1 PROJECTILE","ADD ONE SHOT PER VOLLEY",null));
        List<Reward> supplies=new ArrayList<Reward>();
        for(ItemInfo item:items.getItemInfos())if(item.activationMode!=ActivationMode.EQUIPPED
                &&items.checkGrant(request(item.itemId)).status==GrantCheckStatus.ELIGIBLE){
            String hint=item.description.toUpperCase(java.util.Locale.ROOT);
            supplies.add(new Reward(item.itemId,item.displayName.toUpperCase(java.util.Locale.ROOT),hint,item.itemId));
        }
        Collections.shuffle(perks,random);Collections.shuffle(supplies,random);
        List<Reward> result=new ArrayList<Reward>();result.add(perks.remove(0));
        if(!supplies.isEmpty())result.add(supplies.remove(0));
        perks.addAll(supplies);Collections.shuffle(perks,random);
        while(result.size()<3 && !perks.isEmpty())result.add(perks.remove(0));
        offers=Collections.unmodifiableList(result);
    }
    public boolean refreshRewards(){if(phase!=Phase.REWARD||refreshed)return false;List<Reward> old=offers;
        for(int i=0;i<20;i++){rollRewards();if(!sameOffers(old,offers))break;}refreshed=true;return true;}
    private boolean sameOffers(List<Reward> a,List<Reward> b){java.util.Set<String> ids=new java.util.HashSet<String>();for(Reward r:a)ids.add(r.id);for(Reward r:b)if(!ids.contains(r.id))return false;return true;}
    public boolean chooseReward(int index){
        if(phase!=Phase.REWARD||index<0||index>=offers.size())return false;
        Reward reward=offers.get(index);
        if(reward.itemId!=null){GrantResult result=items.tryGrant(request(reward.itemId));if(result.status==GrantStatus.REJECTED)return false;itemEvents();}
        else if (reward.id.equals("damage")) {
            damage *= 1.10;
            damageUpgrades++;
            notice = "UPGRADE: DAMAGE +10%";
        } else if (reward.id.equals("speed")) {
            playerSpeed = Math.min(360, playerSpeed * 1.10);
            speedUpgrades++;
            notice = "UPGRADE: SPEED +10%";
        } else if(reward.id.equals("projectile")) {
            projectiles++;
            notice = "UPGRADE: +1 PROJECTILE";
        }else{fireRate*=1.10;notice="UPGRADE: FIRE RATE +10%";}
        noticeClock = 5;
        offers=Collections.emptyList();
        phase=getBoss()==null?Phase.INTERMISSION:Phase.BOSS_INTRO;
        countdown=phase==Phase.BOSS_INTRO?3:2;
        return true;
    }

    private static boolean overlaps(Body a, Body b) {
        return a.x < b.x + b.width && a.x + a.width > b.x
                && a.y < b.y + b.height && a.y + a.height > b.y;
    }

    public void togglePause() {
        if (phase == Phase.OVER) return;
        if (phase == Phase.PAUSED) {
            if(nativeBoss instanceof FinalBoss && bossPausedAt>0)
                ((FinalBoss)nativeBoss).resumeAfterPause(System.currentTimeMillis()-bossPausedAt);
            bossPausedAt=0;phase = resumePhase;
        } else { bossPausedAt=System.currentTimeMillis();resumePhase = phase; phase = Phase.PAUSED; }
    }

    public void finish(String reason) {
        if(phase==Phase.OVER)return;
        phase = Phase.OVER; endReason = reason;
        systems.finish(lives>0 && ("CASHED OUT".equals(reason)||"RUN ENDED".equals(reason)),bulletsFired,kills);
    }
    public Phase getPhase() { return phase; }
    public Body getPlayer() { return player; }
    public List<Body> getEnemies() { return Collections.unmodifiableList(enemies); }
    public List<Body> getShots() { return Collections.unmodifiableList(shots); }
    public boolean isPlayerShot(Body shot) { return shot.vy < 0; }
    public Body getBoss() {
        for (Body enemy : enemies) if (enemy.boss) return enemy;
        return null;
    }
    public long getSeconds() { return (long) elapsed; }
    public long getScore() { return getSeconds() * 10 + kills * 100L+bonusPoints+(long)timeBonus; }
    public int getKills() { return kills; }
    public int getWave() { return wave; }
    public int getWavesUntilReward() { return 3-(wave-1)%3; }
    public int getRemainingEnemies() { return enemies.size(); }
    public int getLives() { return lives; }
    public int getBosses() { return bosses; }
    public int getProjectiles() { return projectiles; }
    public int getDamageUpgrades() { return damageUpgrades; }
    public int getSpeedUpgrades() { return speedUpgrades; }
    public int getNextBossWave() { return wave%5==0?wave:(wave/5+1)*5; }
    public int getBossDamage() { Body boss=getBoss();return boss==null?0:(int)Math.ceil(boss.damage); }
    public int getCountdown() { return Math.max(1, (int) Math.ceil(countdown-0.000001)); }
    public double getDifficulty() { return 1 + Math.floor(elapsed / 30) * 0.05; }
    public boolean isInvulnerable() { return invulnerability > 0; }
    public String getNotice() { return noticeClock > 0 ? notice : ""; }
    public String getEndReason() { return endReason; }
}

public static final class Records extends ModeRecords {
    public Records(Path path) { super(path); }
    public boolean save(Run run) { return super.save(run.getScore(),run.getSeconds(),run.getKills()); }
}

}
