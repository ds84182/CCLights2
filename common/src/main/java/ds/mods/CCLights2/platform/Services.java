package ds.mods.CCLights2.platform;

import java.util.ServiceLoader;

/** Loads the loader-specific service implementations (META-INF/services in the loader jars). */
public final class Services {
	public static final Platform PLATFORM = load(Platform.class);

	private Services() {}

	public static <T> T load(Class<T> type) {
		return ServiceLoader.load(type, type.getClassLoader()).findFirst()
				.orElseThrow(() -> new IllegalStateException("No implementation of " + type.getName()
						+ " found; is a CCLights2 loader jar (fabric/forge) installed rather than the common jar?"));
	}
}
