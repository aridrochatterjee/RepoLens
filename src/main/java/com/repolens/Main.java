package com.repolens;

import com.repolens.server.WebServer;

public class Main {

    public static void main(String[] args) {

        WebServer server = new WebServer(8080);

        server.start();

    }
}