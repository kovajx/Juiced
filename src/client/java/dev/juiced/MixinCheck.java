package dev.juiced;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Our injections are optional ({@code require = 0}), so a Minecraft update that moves a target only
 * switches that one feature off instead of crashing the game. This plugin makes such a miss visible:
 * after a mixin is applied it checks that every {@code juiced$} handler is actually called from the
 * target class, and writes a warning to the log for each one that isn't.
 *
 * <p>Lives outside the mixin package on purpose: Mixin doesn't allow loading classes from there.
 */
public final class MixinCheck implements IMixinConfigPlugin {
	private static final Logger LOGGER = LoggerFactory.getLogger("Juiced");
	private static final String PREFIX = "juiced$";
	private static final Set<String> REPORTED = new HashSet<>();

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		// Accessors add methods that nothing in the target calls, that's expected
		if (mixinClassName.endsWith("Accessor")) {
			return;
		}

		Set<String> called = new HashSet<>();

		for (MethodNode method : targetClass.methods) {
			for (AbstractInsnNode instruction : method.instructions) {
				if (instruction instanceof MethodInsnNode call && call.owner.equals(targetClass.name)) {
					called.add(call.name);
				}
			}
		}

		// Merged handlers keep our name at the end (e.g. "handler$zza000$juiced$noHurtCam")
		for (MethodNode method : targetClass.methods) {
			int at = method.name.indexOf(PREFIX);

			if (at < 0 || called.contains(method.name)) {
				continue;
			}

			String handler = method.name.substring(at);

			if (REPORTED.add(targetClassName + "#" + handler)) {
				LOGGER.warn("Juiced: {} found nothing to hook into in {}. That feature does nothing in this Minecraft version.", handler, targetClassName);
			}
		}
	}

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
