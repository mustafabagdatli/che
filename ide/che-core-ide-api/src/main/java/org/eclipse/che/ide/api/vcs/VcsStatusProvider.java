package org.eclipse.che.ide.api.vcs;

/**
 * Created by ivinokur on 11.07.17.
 */
public interface VcsStatusProvider {

    /**
     * status or null
     * @return
     */
    VcsStatus getVcsStatus();
}
