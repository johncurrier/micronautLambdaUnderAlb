package com.example.fubar;

import lombok.Getter;
import lombok.ToString;

/**
 * Configuration of an application stack as presented in cdk.json.
 *
 * @author John Currier
 */
@Getter
@lombok.Builder
@ToString
class AppStackConfig {
	private final String env;
	private final String accountType;
	private final String vpc;
}