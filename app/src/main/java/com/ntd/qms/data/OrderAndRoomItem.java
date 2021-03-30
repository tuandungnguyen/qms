package com.ntd.qms.data;

public class OrderAndRoomItem {
    int queueNumber;
    int direction;
    int room;


    public int getQueueNumber() {
        return queueNumber;
    }

    public void setQueueNumber(int queueNumber) {
        this.queueNumber = queueNumber;
    }

    public int getDirection() {
        return direction;
    }

    public void setDirection(int direction) {
        this.direction = direction;
    }

    public int getRoom() {
        return room;
    }

    public void setRoom(int room) {
        this.room = room;
    }

    public OrderAndRoomItem(int queueNumber, int direction, int room) {
        this.queueNumber = queueNumber;
        this.direction = direction;
        this.room = room;
    }
}
