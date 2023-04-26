package com.example.fubar.controller;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;

/**
 * Something that responds to / requests, primarily to aid in diagnosing
 * that things are fundamentally working.
 *
 * @author John Currier
 */
@Slf4j
@Controller
public class HomeController {
    @Get
    public Map<String, Object> index() {
        log.info("Root index requested - v1");
        return Collections.singletonMap("message", "Will it Blend?");
    }
}