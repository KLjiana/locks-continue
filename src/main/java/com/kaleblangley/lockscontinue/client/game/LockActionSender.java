package com.kaleblangley.lockscontinue.client.game;

import com.kaleblangley.lockscontinue.lock.puzzle.LockAction;

@FunctionalInterface
public interface LockActionSender {
    void send(LockAction action);
}
