package net.geforcemods.securitycraft.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class SecurityCraftAPI {
	private static List<IExtractionBlock> registeredExtractionBlocks = new ArrayList<>();
	private static List<IPasscodeConvertible> registeredPasscodeConvertibles = new ArrayList<>();
	private static List<IAttackTargetCheck> registeredSentryAttackTargetChecks = new ArrayList<>();
	private static List<IDoorActivator> registeredDoorActivators = new ArrayList<>();
	public static final String IMC_EXTRACTION_BLOCK_MSG = "registerExtractionBlock";
	public static final String IMC_PASSCODE_CONVERTIBLE_MSG = "registerPasscodeConvertible";
	public static final String IMC_SENTRY_ATTACK_TARGET_MSG = "registerSentryAttackTargetCheck";
	public static final String IMC_DOOR_ACTIVATOR_MSG = "registerDoorActivator";

	private SecurityCraftAPI() {}

	public static void registerExtractionBlock(IExtractionBlock extractionBlock) {
		registeredExtractionBlocks.add(extractionBlock);
	}

	public static void registerPasscodeConvertible(IPasscodeConvertible passcodeConvertible) {
		registeredPasscodeConvertibles.add(passcodeConvertible);
	}

	public static void registerSentryAttackTargetCheck(IAttackTargetCheck attackTargetCheck) {
		registeredSentryAttackTargetChecks.add(attackTargetCheck);
	}

	public static void registerDoorActivator(IDoorActivator doorActivator) {
		registeredDoorActivators.add(doorActivator);
	}

	/**
	 * Called at the end of SecurityCraft's initialization, after SecurityCraft's own entries and those of every
	 * "securitycraft" entrypoint ({@link SecurityCraftPlugin}) have been registered. Replaces NeoForge's IMC processing.
	 */
	public static void freeze() {
		registeredExtractionBlocks = Collections.unmodifiableList(registeredExtractionBlocks);
		registeredPasscodeConvertibles = Collections.unmodifiableList(registeredPasscodeConvertibles);
		registeredSentryAttackTargetChecks = Collections.unmodifiableList(registeredSentryAttackTargetChecks);
		registeredDoorActivators = Collections.unmodifiableList(registeredDoorActivators);
	}

	public static List<IExtractionBlock> getRegisteredExtractionBlocks() {
		return registeredExtractionBlocks;
	}

	public static List<IPasscodeConvertible> getRegisteredPasscodeConvertibles() {
		return registeredPasscodeConvertibles;
	}

	public static List<IAttackTargetCheck> getRegisteredSentryAttackTargetChecks() {
		return registeredSentryAttackTargetChecks;
	}

	public static List<IDoorActivator> getRegisteredDoorActivators() {
		return registeredDoorActivators;
	}
}
