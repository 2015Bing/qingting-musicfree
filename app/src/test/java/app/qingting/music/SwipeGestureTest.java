package app.qingting.music;
import org.junit.Test;import static org.junit.Assert.*;
public class SwipeGestureTest {
 @Test public void verticalScrollingDoesNotRevealDelete(){SwipeGesture g=new SwipeGesture(76,8);g.begin(100,100,0);assertFalse(g.move(95,140));assertFalse(g.move(30,180));assertEquals(0,g.finish(false),0);}
 @Test public void leftSwipeRevealsAndRightSwipeCloses(){SwipeGesture g=new SwipeGesture(76,8);g.begin(100,100,0);assertTrue(g.move(40,102));assertEquals(-76,g.finish(false),0);g.begin(40,100,-76);assertTrue(g.move(105,100));assertEquals(0,g.finish(false),0);}
 @Test public void cancelRestoresAndSmallTapDoesNotSwipe(){SwipeGesture g=new SwipeGesture(76,8);g.begin(100,100,0);assertFalse(g.move(96,102));assertEquals(0,g.finish(false),0);g.begin(100,100,0);assertTrue(g.move(0,100));assertEquals(-76,g.offset,0);assertEquals(0,g.finish(true),0);}
}
