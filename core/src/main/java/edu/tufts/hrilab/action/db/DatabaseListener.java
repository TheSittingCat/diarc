/*
 * Copyright © Thinking Robots, Inc., Tufts University, and others 2024.
 */

/**
 * @author luca
 */
package edu.tufts.hrilab.action.db;

/**
 * Interface used by the Database to notify other objects
 * of changes in its contents.
 */
public interface DatabaseListener {

  /**
   * Called whenever an action is added to the database.
   *
   * @param adb action added to the database.
   */
  void actionAdded(ActionDBEntry adb);

  /**
   * Called whenever an action is removed from the database.
   *
   * @param adb action removed.
   */
  void actionRemoved(ActionDBEntry adb);
}
