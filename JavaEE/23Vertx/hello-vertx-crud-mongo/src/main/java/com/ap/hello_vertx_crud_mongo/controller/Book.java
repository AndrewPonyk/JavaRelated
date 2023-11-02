package com.ap.hello_vertx_crud_mongo.controller;

public class Book {
  public Book(){

  }
  private String name;
  private Double price;

  public Double getPrice() {
    return price;
  }

  public void setPrice(Double price) {
    this.price = price;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
