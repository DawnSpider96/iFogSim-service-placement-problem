package org.fog.placement;

/**
 *
 * @author Christian Cabrera <cabrerac@scss.tcd.ie>
 * This class represents an ANT that goes through the graph of nodes in the search process.
 *
 */

public class ANT {
    // If an ant is superior, is allowed to deposit pheromone
    private boolean superior;

    // The memories are stored in stack
    private ANTMemory[] memories;

    // Current node
    private int currentNode;

    // Current edge entity
    private SPPHeuristic.DeviceState fogDevice;

    public ANT(int size) {
        this.memories = new ANTMemory[size];
        this.superior = false;
        this.currentNode = -1;
        this.fogDevice = null;
    }

    public ANTMemory[] getMemories() {
        return memories;
    }

    public void setMemories(ANTMemory[] memories) {
        this.memories = memories;
    }

    public void addMemory(int index, ANTMemory memory) {
        memories[index] = memory;
    }

    public boolean isSuperior() {
        return superior;
    }

    public void setSuperior(boolean superior) {
        this.superior = superior;
    }

    public int getCurrentNode() {
        return currentNode;
    }

    public void setCurrentNode(int currentNode) {
        this.currentNode = currentNode;
    }

    public void reset() {
        this.currentNode = -1;
        this.superior = false;
    }

    public SPPHeuristic.DeviceState getFogDevice() {
        return fogDevice;
    }

    public void setFogDevice(SPPHeuristic.DeviceState fogDevice) {
        this.fogDevice = fogDevice;
    }
}