package com.example.camera6;
import java.util.EventListener;
import java.util.EventObject;
/**
 * Описание реализации класса StartCameraEvent.java: StartCameraEvent.java
 * <p>
 * Это класс событий, который наследует java.util.EventObject
 *
 * @author yongchun.chengyc 13.03.2012 17:17:57
 */
public class StartCameraEvent extends EventObject {
    private boolean isStart = false;

    public StartCameraEvent(Object source, boolean isStart){
        super(source);
        this.isStart = isStart;
    }

    public boolean getStart() {
        return isStart;
    }


    public void setStart(boolean isStart) {
        this.isStart = isStart;
    }
}
