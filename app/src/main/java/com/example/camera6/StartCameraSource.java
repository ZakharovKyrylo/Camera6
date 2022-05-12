package com.example.camera6;

import java.util.Vector;

public class StartCameraSource {

    // Это имитирует класс java.util.Observable
    private Vector<StartCameraEventListener> listeners;

    // Регистрация слушателя
    public synchronized void addStartCameraListener(StartCameraEventListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    // отменить слушателя
    public synchronized void removeStartCameraListener(StartCameraEventListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    // Событие штрафной
    protected void fireWorkspaceStart() {
        if (listeners == null) {
            return;
        }
        StartCameraEvent event = new StartCameraEvent(this, true );
        notifyListeners(event);
    }

    // Событие штрафной
    protected void fireWorkspaceStop() {
        if (listeners == null) {
            return;
        }
        StartCameraEvent event = new StartCameraEvent(this, false );
        notifyListeners(event);
    }


    // вызвать событие
    private synchronized void notifyListeners(StartCameraEvent event) {
        for (StartCameraEventListener listener : listeners) {
            listener.cameraStartEvent(event);
        }

    }

    /**
     * @return the listeners
     */
    public Vector<StartCameraEventListener> getListeners() {
        return listeners;
    }

    /**
     * @param listeners the listeners to set
     */
    public void setListeners(Vector<StartCameraEventListener> listeners) {
        this.listeners = listeners;
    }

    public int getListenerNum() {
        if (listeners != null) {
            return listeners.size();
        }
        return 0;
    }
}
