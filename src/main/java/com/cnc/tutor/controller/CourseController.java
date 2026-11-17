package com.cnc.tutor.controller;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cnc.tutor.service.CourseLoader;
import com.cnc.tutor.service.PageImageService;

/** Accès aux pages du cours (images PNG pour afficher les schémas). */
@RestController
@RequestMapping("/api/cours")
public class CourseController {

    private final PageImageService pageImageService;
    private final CourseLoader courseLoader;

    public CourseController(PageImageService pageImageService, CourseLoader courseLoader) {
        this.pageImageService = pageImageService;
        this.courseLoader = courseLoader;
    }

    @GetMapping(value = "/page/{n}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> page(@PathVariable int n) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)))
                .contentType(MediaType.IMAGE_PNG)
                .body(pageImageService.getPagePng(n));
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of("pages", courseLoader.getPageCount());
    }
}
