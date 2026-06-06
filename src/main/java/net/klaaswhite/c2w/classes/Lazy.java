package net.klaaswhite.c2w.classes;

import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class Lazy<T> {

    @Nullable
    private T value;

    private final Supplier<T> creator;
    private boolean created;

    public Lazy(Supplier<T> creator){
        this.creator = creator;
        value = null;
        created = false;
    }

    public T getValue(){
        if (!created){
            value = this.creator.get();
            created = true;
        }

        return value;
    }
}
