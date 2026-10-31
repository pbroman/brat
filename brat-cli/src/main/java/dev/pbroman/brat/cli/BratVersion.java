package dev.pbroman.brat.cli;

import picocli.CommandLine.IVersionProvider;

/**
 * What {@code brat --version} prints: {@code brat} and the version in the jar's manifest, or
 * {@code brat (development build)} when the command runs from anything but the jar.
 */
final class BratVersion implements IVersionProvider {

    @Override
    public String[] getVersion() {
        var version = BratVersion.class.getPackage().getImplementationVersion();
        return new String[] {"brat " + (version == null ? "(development build)" : version)};
    }
}
