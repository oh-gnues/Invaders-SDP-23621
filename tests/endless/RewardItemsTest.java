package engine;
import screen.EndlessMode.Run;
import screen.EndlessMode.Run.*;
import item.ItemAPI.*;
import java.lang.reflect.*;
import java.util.*;
public final class RewardItemsTest {
    static int checks,ids;
    static void check(boolean value,String text){checks++;if(!value)throw new AssertionError(text);}
    static Object get(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static void set(Object o,String name,Object value)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    static Run fresh()throws Exception{Run r=new Run(432,497,new Random(42));set(r,"phase",Phase.PLAYING);r.update(.001,0,false);return r;}
    static void grant(Run r,String id){check(r.items.tryGrant(new GrantRequest("test-"+(++ids),id,GrantSource.REWARD,GrantTiming.NOW)).status!=GrantStatus.REJECTED,"Grant "+id);}
    @SuppressWarnings("unchecked") static void clear(Run r)throws Exception{((List<Body>)get(r,"enemies")).clear();}
    static void reward(Run r,int wave)throws Exception{set(r,"phase",Phase.PLAYING);set(r,"wave",wave);clear(r);r.update(.001,0,false);}
    static void advance(Run r,int frames){for(int i=0;i<frames;i++)r.update(.1,0,false);}
    @SuppressWarnings("unchecked") public static void main(String[]args)throws Exception {
        Run r=fresh();reward(r,2);check(r.getPhase()==Phase.INTERMISSION,"No draft after two waves");
        reward(r,3);check(r.getPhase()==Phase.REWARD,"Draft after third wave");
        List<Reward> offers=(List<Reward>)get(r,"offers");check(offers.size()==3,"Exactly three offers");
        check(offers.stream().map(o->o.id).distinct().count()==3,"Distinct offers");
        check(offers.stream().anyMatch(o->o.itemId==null)&&offers.stream().anyMatch(o->o.itemId!=null),"Mixed upgrade and item offers");
        long seconds=r.getSeconds(),score=r.getScore();double x=r.getPlayer().x;advance(r,100);
        check(r.getSeconds()==seconds&&r.getScore()==score&&r.getPlayer().x==x,"Draft freezes all clocks and movement");
        check(r.refreshRewards(),"First refresh allowed");check(!r.refreshRewards(),"Second refresh denied");
        check(r.chooseReward(1),"One choice applies");check(!r.chooseReward(0),"Second choice denied");
        check(r.getPhase()==Phase.INTERMISSION,"Choice returns to safe countdown");
        reward(r,6);check(r.refreshRewards(),"Next draft receives new refresh");

        r=fresh();set(r,"lives",2);grant(r,"life");check(r.getLives()==3,"Life restored");
        score=r.getScore();grant(r,"life");check(r.getScore()==score+((Number)get(r,"lifeBonus")).longValue(),"Full life converts to configured score");
        grant(r,"rapid_fire");check(r.items.getModifiers().fireRateBonus>0,"Rapid fire active");
        grant(r,"bullet_speed");check(r.items.getModifiers().bulletSpeedBonus>0,"Bullet speed active");
        r.update(.01,0,true);check((Double)get(r.getShots().get(0),"vy") < -420,"Bullet speed reaches real projectiles");

        r=fresh();grant(r,"boost");r.useItem(0);x=r.getPlayer().x;r.update(.05,1,true);
        check(Math.abs(r.getPlayer().x-x-9)<.01,"Boost accelerates actual movement by 50 percent");
        check((Double)get(r,"shotClock")<.51,"Boost accelerates actual shooting");
        r=fresh();grant(r,"freeze");r.useItem(0);set(r,"moveRemainder",1.0);x=r.getEnemies().get(0).x;r.update(.05,0,false);
        check(r.getEnemies().get(0).x==x,"Freeze stops enemies");
        long remaining=r.items.getView().effects.get(0).remainingMillis;r.togglePause();advance(r,100);
        check(r.items.getView().effects.get(0).remainingMillis==remaining,"Pause freezes effect timers");
        r.togglePause();reward(r,3);remaining=r.items.getView().effects.get(0).remainingMillis;advance(r,100);
        check(r.items.getView().effects.get(0).remainingMillis==remaining,"Draft freezes effect timers");

        r=fresh();grant(r,"shield");r.useItem(0);check(r.items.tryBlockHit(),"Shield blocks one hit");check(!r.items.tryBlockHit(),"Shield has one charge");
        r=fresh();set(r,"elapsed",10.0);grant(r,"score_boost");r.useItem(0);r.update(.001,0,true);
        Body enemy=r.getEnemies().get(0),shot=r.getShots().get(0);shot.x=enemy.x;shot.y=enemy.y+4;r.update(.001,0,false);
        check(r.getKills()==1&&r.getScore()>=300,"Score boost doubles actual kill score");

        r=fresh();grant(r,"shield");grant(r,"freeze");grant(r,"boost");reward(r,3);
        offers=(List<Reward>)get(r,"offers");check(offers.stream().filter(o->o.itemId!=null).allMatch(o->rItem(o.itemId)!=ActivationMode.MANUAL),"Full inventory excludes stored-item rewards");
        r=fresh();for(int i=0;i<200;i++)r.items.onEnemyDefeated(DropSource.REGULAR_ENEMY,200,120);
        check(r.items.getView().drops.size()>0&&r.items.getView().drops.size()<200,"Regular kills roll random drops");
        r=fresh();set(r,"wave",4);clear(r);r.update(.001,0,false);
        while(r.getPhase()==Phase.BOSS_INTRO)r.update(.1,0,false);
        Body boss=r.getBoss();boss.hp=1;r.update(.001,0,true);
        shot=r.getShots().get(0);shot.x=boss.x+5;shot.y=boss.y+5;r.update(.001,0,false);
        check(r.getBosses()==1&&r.getDamageUpgrades()==0&&r.getProjectiles()==1,"Boss grants no automatic upgrades");
        check(!r.items.getView().drops.isEmpty(),"Boss drops a supply");
        collisionWaveProgression();
        System.out.println("PASS: "+checks+" item / reward draft checks");
    }
    /** Clear fifteen waves via projectile collisions, covering boss and reward checkpoints together. */
    static void collisionWaveProgression()throws Exception {
        Run r=fresh();int attacks=0;
        for(int wave=1;wave<=15;wave++) {
            while(r.getWave()==wave) {
                if(r.getPhase()==Phase.INTERMISSION || r.getPhase()==Phase.BOSS_INTRO){r.update(.1,0,false);continue;}
                if(r.getPhase()!=Phase.PLAYING)throw new AssertionError("Unexpected phase before wave "+wave+" clear");
                if(++attacks>600)throw new AssertionError("Wave progression stalled");
                if(wave%5==0)check(r.getEnemies().size()==1 && r.getBoss()!=null,"Every fifth wave contains only its boss");
                else if(r.getBoss()!=null)throw new AssertionError("Unexpected boss in wave "+wave);
                set(r,"shotClock",0.0);r.update(.001,0,true);
                Body shot=r.getShots().stream().filter(r::isPlayerShot).findFirst().orElse(null);
                if(shot==null)continue;
                Body target=r.getBoss()==null?r.getEnemies().get(0):r.getBoss();
                boolean boss=target.boss;target.hp=1;
                shot.x=target.x+2;shot.y=target.y+2;
                r.update(.001,0,false);
                if(boss)check(r.getWave()==wave+1,"Boss defeat clears its wave exactly once");
            }
            check(r.getWave()==wave+1,"Clearing formation advances exactly one wave");
            if(wave%3==0) {
                check(r.getPhase()==Phase.REWARD,"Actual wave "+wave+" clear opens a reward");
                check(((List<?>)get(r,"offers")).size()==3,"Actual checkpoint has three choices");
                check(r.refreshRewards()&&!r.refreshRewards(),"Actual checkpoint allows exactly one refresh");
                check(r.chooseReward(0)&&!r.chooseReward(1),"Actual checkpoint grants exactly one choice");
            } else check(r.getPhase()==((wave+1)%5==0?Phase.BOSS_INTRO:Phase.INTERMISSION),"Other waves wait or introduce the next boss without a draft");
            if((wave+1)%5==0)check(r.getPhase()==Phase.BOSS_INTRO,"Next boss introduction survives the reward checkpoint");
            while(r.getPhase()==Phase.INTERMISSION || r.getPhase()==Phase.BOSS_INTRO)r.update(.1,0,false);
            check(r.getWavesUntilReward()==3-wave%3,"HUD reports next reward after wave "+wave);
        }
        check(r.getBosses()==3,"Waves 5, 10 and 15 each grant exactly one boss victory");
    }
    static ActivationMode rItem(String id){return Arrays.asList("shield","freeze","boost","score_boost").contains(id)?ActivationMode.MANUAL:ActivationMode.ON_PICKUP;}
}
