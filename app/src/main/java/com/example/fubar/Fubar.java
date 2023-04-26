package com.example.fubar;

import io.micronaut.runtime.Micronaut;

/**
 * This class is here just for running Fubar locally.
 * It doesn't get used when we're running as an AWS Lambda.
 *
 * @author John Currier
 */
public class Fubar {
	public static void main(String[] args) {
		Micronaut.build(args)
			.eagerInitSingletons(true)
			.mainClass(Fubar.class)
			.start();
	}
}