package com.mastfrog.acteur.annotations;

import com.google.inject.ImplementedBy;
import com.google.inject.name.Named;
import com.mastfrog.acteur.Application;
import com.mastfrog.acteur.ImplicitBindings;
import com.mastfrog.acteur.Page;
import static com.mastfrog.acteur.annotations.GenericApplicationModule.EXCLUDED_CLASSES;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;

/**
 * An application which looks up its pages using registry files on the
 * classpath, generated by an annotation processor which processes
 * &#064;HttpCall annotations.
 *
 * @author Tim Boudreau
 */
public class GenericApplication extends Application {

    public GenericApplication(boolean withHelp) {
        // Even though the varags version is semantically identical, Guice will
        // attempt to inject a Class[] into it and fail
        this(new GenericApplicationSettingsImpl(), new Class<?>[0]);
    }

    public GenericApplication() {
        // Even though the varags version is semantically identical, Guice will
        // attempt to inject a Class[] into it and fail
        this(new Class<?>[0]);
    }

    public GenericApplication(@Named(EXCLUDED_CLASSES) Class<?>... excludePages) {
        this(new GenericApplicationSettingsImpl(), excludePages);
    }

    @Inject
    public GenericApplication(GenericApplicationSettings settings, @Named(EXCLUDED_CLASSES) Class<?>... excludePages) {
        Set<Class<?>> excluded = new HashSet<>(Arrays.asList(excludePages));
        ImplicitBindings implicit = getClass().getAnnotation(ImplicitBindings.class);
        Set<Class<?>> alreadyBound = implicit == null ? Collections.<Class<?>>emptySet()
                : new HashSet<>(Arrays.asList(implicit.value()));
        System.out.println("Generic app with the following HTTP calls:");
        for (Class<? extends Page> pageType : new HttpCallRegistryLoader(getClass())) {
            if (!alreadyBound.contains(pageType) && !excluded.contains(pageType)) {
                System.out.println("  " + pageType.getSimpleName());
                add(pageType);
            }
        }
        if (settings.helpEnabled) {
            add(Application.helpPageType());
        }
        if (settings.corsEnabled) {
            super.enableDefaultCorsHandling();
        }
    }

    @ImplementedBy(GenericApplicationSettingsImpl.class)
    public static class GenericApplicationSettings {

        public final boolean corsEnabled;
        public final boolean helpEnabled;

        public GenericApplicationSettings(boolean corsEnabled, boolean helpEnabled) {
            this.corsEnabled = corsEnabled;
            this.helpEnabled = helpEnabled;
        }
    }

    static class GenericApplicationSettingsImpl extends GenericApplicationSettings {

        GenericApplicationSettingsImpl() {
            super(false, false);
        }
    }
}
