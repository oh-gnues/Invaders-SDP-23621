package endless;

import engine.*;
import entity.*;
import item.ItemSystem;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.*;

/** Only connects the four existing shop VFX; rendering remains owned by the VFX team. */
public final class ShopEffects {
    private final boolean comet;
    private final GhostTrail ghost;
    private final LightningChain lightning;
    private WarpIn warp;
    private final boolean warpEnabled;
    private final Clock clock=new Clock();
    private boolean playing, advancing, planned, hidden;
    private double frames;

    ShopEffects(Set<String> enabled) {
        comet=enabled.contains("comet_trail");
        ghost=enabled.contains("ghost_trail")?new GhostTrail():null;
        lightning=enabled.contains("lightning_chain")?new LightningChain():null;
        warpEnabled=enabled.contains("warp_in");
        warp=warpEnabled?new WarpIn():null;
    }

    public void frame(Ship ship, boolean playing, boolean advancing, int lives,
            double countdown, double seconds) {
        this.playing=playing;this.advancing=advancing;
        double elapsed=advancing?Math.max(0,Math.min(.1,seconds)):0;
        clock.sync(elapsed,this::shift);
        if(ghost!=null && !playing)ghost.clear();
        if(lightning!=null && playing) {
            frames+=elapsed*60;
            while(frames>=1){lightning.update();frames--;}
        }
        if(warp!=null && advancing) {
            if(!planned && !playing){warp.startLevel((int)Math.ceil(countdown*1000));planned=true;}
            hidden=warp.update(ship,lives>0) && (!playing || ship.isDestroyed());
        }
    }
    public void beginWave() {
        if(ghost!=null)ghost.clear();
        if(lightning!=null)lightning.reset();
        if(warpEnabled)warp=new WarpIn();
        planned=false;hidden=false;frames=0;
    }
    public void playerHit(Ship ship,int lives) {
        if(ghost!=null)ghost.clear();
        if(lightning!=null)lightning.onEvent(GameEvents.Type.PLAYER_HIT,lives);
        if(warp!=null)warp.update(ship,lives>0);
    }
    public void enemyDefeated(Entity dead,Collection<? extends Entity> enemies) {
        if(lightning==null)return;
        clock.sync(0,this::shift);
        List<Point> centers=new ArrayList<>();
        for(Entity e:enemies)if(e!=dead && !destroyed(e))
            centers.add(new Point(e.getPositionX()+e.getWidth()/2,e.getPositionY()+e.getHeight()/2));
        lightning.onKill(dead.getPositionX()+dead.getWidth()/2,dead.getPositionY()+dead.getHeight()/2,centers);
    }
    private static boolean destroyed(Entity e) {
        return e instanceof EnemyShip && ((EnemyShip)e).isDestroyed()
            || e instanceof Boss && ((Boss)e).isDestroyed();
    }
    public boolean hidesShip() { return advancing && hidden; }
    public void drawBullet(DrawManager drawing,Bullet bullet) {
        if(comet && playing)draw(drawing,()->drawing.drawBulletTrail(bullet));
    }
    public void drawGhost(DrawManager drawing,Ship ship,Color color,ItemSystem items) {
        if(ghost!=null && playing)draw(drawing,()->ghost.draw(drawing,ship,color,GhostTrail.isBoosting(items)));
    }
    public void drawLightning(DrawManager drawing) {
        if(lightning!=null && playing)draw(drawing,()->drawing.drawLightningChain(lightning));
    }
    public void drawWarp(DrawManager drawing,Ship ship,Color color) {
        if(warp!=null && advancing)draw(drawing,()->drawing.drawWarpIn(warp,ship,color,130));
    }
    private static void draw(DrawManager drawing,Runnable action) {
        Graphics2D original=(Graphics2D)SharedAccess.get(DrawManager.class,"backBufferGraphics");
        Graphics2D copy=(Graphics2D)original.create();
        // Effects cannot cover the health HUD or leak native Graphics state to other elements.
        java.awt.image.BufferedImage buffer=(java.awt.image.BufferedImage)SharedAccess.get(DrawManager.class,"backBuffer");
        copy.clipRect(0,130,buffer.getWidth(),Math.max(0,buffer.getHeight()-130));
        try { SharedAccess.drawWith(copy,action); } finally { copy.dispose(); }
    }
    private void shift(long amount) {
        if(warp!=null)SharedAccess.shiftVisualTime(warp,"startTime",amount);
        if(lightning!=null) {
            SharedAccess.shiftVisualTime(lightning,"lastKillTime",amount);
            for(Object strike:(List<?>)SharedAccess.get(lightning,"strikes"))SharedAccess.shiftVisualTime(strike,"born",amount);
        }
    }
    public void clear() {
        if(ghost!=null)ghost.clear();
        if(lightning!=null)lightning.reset();
        if(warpEnabled)warp=new WarpIn();
        playing=false;advancing=false;hidden=false;
    }
    private static final class Clock {
        private long last=System.currentTimeMillis();
        private double remainder;
        void sync(double seconds,java.util.function.LongConsumer shift) {
            long now=System.currentTimeMillis();remainder+=seconds*1000;
            long elapsed=(long)remainder;remainder-=elapsed;
            shift.accept(now-last-elapsed);last=now;
        }
    }
}
