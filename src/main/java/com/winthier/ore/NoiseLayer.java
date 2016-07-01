package com.winthier.ore;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class NoiseLayer {
    final OpenSimplexNoise noise;

    public static NoiseLayer of(long seed) {
        return new NoiseLayer(new OpenSimplexNoise(seed));
    }

    double at(int x, int y, int z, double featureSize) {
        return noise.eval(x/featureSize, y/featureSize, z/featureSize);
    }

    double abs(int x, int y, int z, double featureSize) {
        return Math.abs(at(x, y, z, featureSize));
    }
}
