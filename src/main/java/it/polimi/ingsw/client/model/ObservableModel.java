package it.polimi.ingsw.client.model;

import it.polimi.ingsw.client.view.observers.ModelObserver;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This abstract class provides a foundation for implementing observable models in your application.
 * Models are updated from the network thread while views register and unregister on the UI thread,
 * so the observer list is copy-on-write: notifying never trips over a concurrent add/remove.
 */
public abstract class ObservableModel {

    private final List<ModelObserver> observers;

    public ObservableModel() {
        observers = new CopyOnWriteArrayList<>();
    }

    /**
     * Registers an observer with this model. The observer will be notified whenever the model's data changes.
     * @param observer The ModelObserver to unregister.
     */
    public void addObserver(ModelObserver observer) {
        observers.add(observer);
    }

    public void removeObserver(ModelObserver observer) {
        observers.remove(observer);
    }

    /**
     * Notifies all registered observers that the model's data has changed. This method iterates through the list of observers.
     */
    protected void notifyObservers() {
        for (ModelObserver observer : observers) {
            observer.update();
        }
    }
}
