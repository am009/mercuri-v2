package backend.lsra;

import java.util.HashMap;
import java.util.Map;

import backend.AsmBlock;

public class MoveResolver {

    public Map<LiveInterval, LiveInterval> map = new HashMap<>();
    public void addMapping(LiveInterval fromInterval, LiveInterval toInterval) {
        map.put(fromInterval, toInterval);
    }

    public void resolveMappings() {
    }

    // the moves are inserted either at the end of block from or at the beginning of block to, 
    // depending on the control flow 
    public void findInsertPos(AsmBlock fromBlock, AsmBlock toBlock) {
        
    }

}
