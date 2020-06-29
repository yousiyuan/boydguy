package com.boydguy.frontend.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/example")
public class ExampleWebController {

    @GetMapping("/test")
    public String testPage() {
        return "hello world";
    }

}
