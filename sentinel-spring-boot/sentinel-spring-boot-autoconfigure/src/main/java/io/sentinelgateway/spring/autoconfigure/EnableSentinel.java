package io.sentinelgateway.spring.autoconfigure;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Optional annotation for explicit opt-in when auto-configuration is disabled.
 * Add to your {@code @SpringBootApplication} class.
 *
 * <pre>
 * {@literal @}SpringBootApplication
 * {@literal @}EnableSentinel
 * public class MyApplication { ... }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(SentinelAutoConfiguration.class)
public @interface EnableSentinel {}
