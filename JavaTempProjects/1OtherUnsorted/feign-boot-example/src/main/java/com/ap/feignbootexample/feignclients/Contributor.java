package com.ap.feignbootexample.feignclients;

class Contributor {
    String login;
    int contributions;

    @Override
    public String toString() {
        return "Contributor{" +
                "login='" + login + '\'' +
                ", contributions=" + contributions +
                '}';
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public int getContributions() {
        return contributions;
    }

    public void setContributions(int contributions) {
        this.contributions = contributions;
    }
}
