package com.intellij.util.containers;

import com.intellij.util.Assertion;
import junit.framework.Assert;
import junit.framework.TestCase;

public class QueueTest extends TestCase {
  private Assertion CHECK = new Assertion();
  private com.intellij.util.containers.Queue myQueue = new com.intellij.util.containers.Queue(1);

  public void testEmpty() {
    Assert.assertEquals(0, myQueue.size());
    Assert.assertTrue(myQueue.isEmpty());
    CHECK.empty(myQueue.toList());
  }

  public void testAddingGetting() {
    String element = "1";
    myQueue.addLast(element);
    Assert.assertEquals(1, myQueue.size());
    Assert.assertFalse(myQueue.isEmpty());
    CHECK.singleElement(myQueue.toList(), element);
    Assert.assertSame(element, myQueue.pullFirst());
    testEmpty();
    myQueue.addLast("2");
    Assert.assertEquals(1, myQueue.size());
    myQueue.addLast("3");
    Assert.assertEquals(2, myQueue.size());
    CHECK.compareAll(new Object[]{"2", "3"}, myQueue.toList());
    Assert.assertEquals("2", myQueue.pullFirst());
    Assert.assertEquals("3", myQueue.pullFirst());
    testEmpty();
  }

  public void testCycling() {
    com.intellij.util.containers.Queue queue = new com.intellij.util.containers.Queue(10);
    for (int i = 0; i < 9; i++) {
      queue.addLast(String.valueOf(i));
      Assert.assertEquals(i+1, queue.size());
    }
    CHECK.count(9, queue.toList());
    for (int i = 0; i < 9; i++) {
      Object first = queue.pullFirst();
      Assert.assertEquals(String.valueOf(i), first);
      Assert.assertEquals(8, queue.size());
      queue.addLast(first);
    }
    for (int i = 0; i < 9; i++) {
      Assert.assertEquals(String.valueOf(i), queue.pullFirst());
      Assert.assertEquals(8 - i, queue.size());
    }

  }
}
