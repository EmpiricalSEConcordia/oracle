/*
 * Copyright 2015-2017 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.jupiter.migrationsupport.rules.adapter;

import static org.junit.platform.commons.meta.API.Status.INTERNAL;

import org.junit.jupiter.migrationsupport.rules.member.TestRuleAnnotatedMember;
import org.junit.platform.commons.meta.API;
import org.junit.rules.Verifier;

/**
 * @since 5.0
 */
@API(status = INTERNAL, since = "5.0")
public class VerifierAdapter extends AbstractTestRuleAdapter {

	public VerifierAdapter(TestRuleAnnotatedMember annotatedMember) {
		super(annotatedMember, Verifier.class);
	}

	@Override
	public void after() {
		executeMethod("verify");
	}

}
