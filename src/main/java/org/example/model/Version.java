package org.example.model;


import java.time.LocalDate;
import java.util.ArrayList;

public class Version {
    public int index;
    public String id;
    public String name;
    public LocalDate date;

    public Version( String id, String name, LocalDate date) {
        this.id = id;
        this.name = name;
        this.date = date;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public int getIndex() {
        return index;
    }

    public String getId() {
        return id;
    }

    public String getName() {
      return name;
    }

    public LocalDate getDate() {
        return date;
    }
}
