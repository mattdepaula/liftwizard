/*
 * Copyright 2025 Craig Motlin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.liftwizard.rewrite.logging;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class ParameterizedLoggingTest implements RewriteTest {

	@Override
	public void defaults(RecipeSpec spec) {
		spec
			.recipe(new ParameterizedLogging("org.slf4j.Logger info(..)", null))
			.parser(JavaParser.fromJavaVersion().classpath("slf4j-api"));
	}

	@DocumentExample
	@Test
	void replacePatterns() {
		this.rewriteRun(
				java(
					"""
					import org.slf4j.Logger;
					import org.slf4j.LoggerFactory;

					class Test {
					    private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);

					    void stringConcatenationToParameterized(String name) {
					        LOGGER.info("Hello " + name);
					    }

					    void multipleVariablesConcatenated(String first, String last) {
					        LOGGER.info("User " + first + " " + last + " logged in");
					    }

					    void concatenationWithThrowableAsLastArg(String name, Exception e) {
					        LOGGER.info("Error for " + name, e);
					    }
					}
					""",
					"""
					import org.slf4j.Logger;
					import org.slf4j.LoggerFactory;

					class Test {
					    private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);

					    void stringConcatenationToParameterized(String name) {
					        LOGGER.info("Hello {}", name);
					    }

					    void multipleVariablesConcatenated(String first, String last) {
					        LOGGER.info("User {} {} logged in", first, last);
					    }

					    void concatenationWithThrowableAsLastArg(String name, Exception e) {
					        LOGGER.info("Error for {}", name, e);
					    }
					}
					"""
				)
			);
	}

	@Test
	void doNotReplaceInvalidPatterns() {
		this.rewriteRun(
				java(
					"""
					import org.slf4j.Logger;
					import org.slf4j.LoggerFactory;

					class Test {
					    private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);

					    void doesNotTransformStringArgument() {
					        LOGGER.info("Simple message");
					    }

					    void doesNotTransformAlreadyParameterized(String name) {
					        LOGGER.info("Hello {}", name);
					    }
					}
					"""
				)
			);
	}

	@Test
	void removeToStringWhenEnabled() {
		this.rewriteRun(
				(spec) -> spec.recipe(new ParameterizedLogging("org.slf4j.Logger info(..)", true)),
				java(
					"""
					import org.slf4j.Logger;
					import org.slf4j.LoggerFactory;

					class Test {
					    private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);

					    void test(Object obj) {
					        LOGGER.info("Value: " + obj.toString());
					    }
					}
					""",
					"""
					import org.slf4j.Logger;
					import org.slf4j.LoggerFactory;

					class Test {
					    private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);

					    void test(Object obj) {
					        LOGGER.info("Value: {}", obj);
					    }
					}
					"""
				)
			);
	}

	/**
	 * Defect proofs for arguments whose type degrades to {@link org.openrewrite.java.tree.JavaType.Unknown}.
	 * These need their own parser/validation configuration ({@link TypeValidation#none()} plus an
	 * intentionally unresolved exception type), so they are isolated here rather than mixed into the
	 * fully type-validated {@link ParameterizedLoggingTest#replacePatterns} example.
	 */
	@Nested
	class UnresolvedType implements RewriteTest {

		@Override
		public void defaults(RecipeSpec spec) {
			spec
				.recipe(new ParameterizedLogging("org.slf4j.Logger info(..)", null))
				.parser(JavaParser.fromJavaVersion().classpath("slf4j-api"))
				.typeValidationOptions(TypeValidation.none());
		}

		/**
		 * When the exception argument's type degrades to {@code Unknown} (its type
		 * {@code com.unresolved.AppException} is neither on the classpath nor stubbed),
		 * {@code TypeUtils.isAssignableTo("java.lang.Throwable", ..)} returns false, the exception is
		 * misclassified as a regular argument, and the synthesized call reorders the arguments to the
		 * wrong {@code LOGGER.info("Error for {}", e, name)}. This asserts the correct ordering, so it
		 * fails until the recipe treats an unresolved trailing argument conservatively.
		 */
		@Test
		void throwableWithUnknownTypeKeepsTrailingPosition() {
			this.rewriteRun(
					java(
						"""
						import org.slf4j.Logger;
						import org.slf4j.LoggerFactory;
						import com.unresolved.AppException;

						class Test {
						    private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);

						    void test(String name, AppException e) {
						        LOGGER.info("Error for " + name, e);
						    }
						}
						""",
						"""
						import org.slf4j.Logger;
						import org.slf4j.LoggerFactory;
						import com.unresolved.AppException;

						class Test {
						    private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);

						    void test(String name, AppException e) {
						        LOGGER.info("Error for {}", name, e);
						    }
						}
						"""
					)
				);
		}
	}
}
