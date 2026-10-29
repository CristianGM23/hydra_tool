import type { GrammarToolchainProfile } from './types';

/** Summarizes a toolchain profile for the selector: provider, build system, target and the runtimes the generated project requires. */
export function toolchainProfileDescription(profile:GrammarToolchainProfile):string{
    const recommendedNode=profile.settings?.recommendedNodeVersion;
    const node=profile.nodeVersion?` · Node ${profile.nodeVersion}${recommendedNode?` (recommended ${recommendedNode})`:''}`:'';
    return `Provider ${profile.providerVersion} · ${profile.buildSystem} · target ${profile.targetLanguage}${profile.javaVersion?` · Java ${profile.javaVersion}`:''}${node}`;
}
