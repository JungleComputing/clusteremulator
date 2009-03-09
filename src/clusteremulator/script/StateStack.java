package clusteremulator.script;

import java.util.LinkedList;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StateStack<STATE> {

    private Logger logger = LoggerFactory.getLogger(StateStack.class);

    private LinkedList<STATE> list;

    public StateStack(STATE initialState) {
        list = new LinkedList<STATE>();

        list.addLast(initialState);
    }

    public synchronized boolean is(STATE state) {
        return list.getLast().equals(state);
    }

    public synchronized void set(STATE newState) {
        logger.debug("setting state to " + newState);

        if (!is(newState)) {
            list.removeLast();
            list.addLast(newState);
            notifyAll();
        }

        logger.debug("state: " + list);
    }

    public synchronized void push(STATE state) {
        logger.debug("pushing state " + state);

        list.add(state);

        logger.debug("state: " + list);

        notifyAll();
    }

    public synchronized void pop() {
        logger.debug("popping state");

        list.removeLast();

        logger.debug("state: " + list);

        notifyAll();
    }

    public synchronized void waitForAny(Set<STATE> states) {
        while (!states.contains(list.getLast())) {
            try {
                logger.debug("state: " + list);
                logger.debug("waiting until current state (" + list.getLast()
                        + ") becomes any of " + states);
                wait();
            } catch (InterruptedException ignored) {
                // ignore
            }
        }
    }

    public synchronized void waitUntilNot(STATE state) {
        while (is(state)) {
            try {
                logger.debug("state: " + list);
                logger.debug("waiting until current state (" + list.getLast()
                        + ") changes");
                wait();
            } catch (InterruptedException ignored) {
                // ignore
            }
        }
    }

}
