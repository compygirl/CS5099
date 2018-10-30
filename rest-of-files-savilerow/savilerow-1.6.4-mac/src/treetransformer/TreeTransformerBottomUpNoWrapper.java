package savilerow.treetransformer;
/*

    Savile Row http://savilerow.cs.st-andrews.ac.uk/
    Copyright (C) 2014, 2015 Peter Nightingale
    
    This file is part of Savile Row.
    
    Savile Row is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    
    Savile Row is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
    
    You should have received a copy of the GNU General Public License
    along with Savile Row.  If not, see <http://www.gnu.org/licenses/>.

*/


import savilerow.expression.*;
import savilerow.CmdFlags;
import java.util.ArrayList;
import java.util.HashMap;
import savilerow.model.*;

// For Simplify. Only allows replacement of the current node.
// Does not wrap the subtree as TreeTransformerBottomUp does. 
// However it does copy the subtree. 

public abstract class TreeTransformerBottomUpNoWrapper extends TreeTransformer
{
    /**
	 * Traverse the tree rooted at e, and apply processNode to every node.
	 * Processes children before the parent.
	 * 
	 */
	
	Model m;
	TreeTransformerBottomUpNoWrapper(Model _m) {
	    m=_m;
	}
	
    public ASTNode transform(ASTNode e)
	{
	    changedTree=false;   // clear flag. 
	    assert e!=null;
	    
	    // Store the parent.
	    ASTNode par=e.getParent();
	    int chno=e.getChildNo();
	    
	    if(par!=null) {
	        e=e.copy();   // do not change in place.
	        e.setParent(par);
	        e.setChildNo(chno);
	    }
	    
	    ASTNode rep=recursiveSearch(e);
	    
	    while(rep!=e) {
            // Is rep different?
            // assert !rep.equals(e);   // Needs to be taken out later for efficiency
            e=rep;
            changedTree=true;
            
            if(par!=null) {
                e.setParent(par);      // set up back pointer so context methods work. 
                e.setChildNo(chno);
            }
            
            rep=recursiveSearch(e);
        }
        
        if(par!=null) {
            // Clear the junk parent ptr so that rep is not copied.
            rep.setParent(null);
        }
        
	    return rep;
	}
	
	// returns a replacement for curnode. Does not make the replacement itself.
	private ASTNode recursiveSearch(ASTNode curnode) {
	    
	    for(int i=0; i<curnode.numChildren(); i++) {
            ASTNode newchild=recursiveSearch(curnode.getChild(i));
            
            // Keep processing the child until it stops changing. 
            while(newchild != curnode.getChild(i)) {
                // assert ! newchild.equals(curnode.getChild(i));   // If they are not equal by reference, they should not be equal by .equal as well.
                
                curnode.setChild(i, newchild);
                
                newchild=recursiveSearch(curnode.getChild(i));
            }
        }
	    
        // Done all the children, now do this node.
        
        NodeReplacement r=processNode(curnode);
        
        if(r!=null) {
            changedTree=true;
            assert r.rel_context==null;
            assert r.new_constraint==null;
            if(r.current_node != null) {
                // assert !r.current_node.equals(curnode);    //  remove for efficiency. (And also because it fires when using TFixSTRef.
                return r.current_node;
            }
        }
        
        return curnode;
    }
	
}
