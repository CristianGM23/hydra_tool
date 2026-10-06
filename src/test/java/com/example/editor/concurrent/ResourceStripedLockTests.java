package com.example.editor.concurrent;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
class ResourceStripedLockTests {
 @Test void usesFixedStorageRegardlessOfResourceCardinality(){var locks=new ResourceStripedLock(128);for(int i=0;i<100_000;i++)locks.lockFor("resource-"+i);assertThat(locks.stripeCount()).isEqualTo(128);}
 @Test void sameResourceAlwaysUsesSameLock(){var locks=new ResourceStripedLock(64);assertThat(locks.lockFor("meta:a")).isSameAs(locks.lockFor("meta:a"));}
}
