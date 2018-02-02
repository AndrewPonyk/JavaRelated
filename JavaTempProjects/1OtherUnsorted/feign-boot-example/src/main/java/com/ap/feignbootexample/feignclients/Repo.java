package com.ap.feignbootexample.feignclients;

class Repo{
    @Override
    public String toString() {
        return "Repo{" +
                "name='" + name + '\'' +
                '}';
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    String name;
}
