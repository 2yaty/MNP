package com.fourgtss.mnp;

import org.springframework.boot.SpringApplication;

public class TestMnpBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(MnpBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
