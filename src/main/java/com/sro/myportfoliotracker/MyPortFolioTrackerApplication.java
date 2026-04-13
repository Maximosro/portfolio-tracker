package com.sro.myportfoliotracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
@EnableScheduling
public class MyPortFolioTrackerApplication {

	public static void main(String[] args) {
		// Asegurar que el directorio data/ existe antes de que SQLite intente crear la BD
		try { Files.createDirectories(Path.of("./data")); } catch (final Exception ignored) {}
		SpringApplication.run(MyPortFolioTrackerApplication.class, args);
	}

}
