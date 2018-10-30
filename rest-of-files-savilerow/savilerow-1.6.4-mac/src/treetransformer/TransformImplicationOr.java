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
import savilerow.model.*;

import java.util.*;

// A -> B   ----->  not(A) \/ B
// A <-> B    ---->   not(A)\/ B   /\   A \/ not(B)
// One of the 'branching' transformations. 

public class TransformImplicationOr extends TreeTransformerBottomUpNoWrapper
{
    public TransformImplicationOr(boolean _Iff) {
        super(null);
        Iff=_Iff;
    }
    
    private boolean Iff;   //  Second case in comment above.  
    
	protected NodeReplacement processNode(ASTNode curnode)
	{
	    if( (!Iff) && curnode instanceof Implies ) {
	        return new NodeReplacement(new Or(new Negate(curnode.getChild(0)), curnode.getChild(1)));
	        
	    }
	    
	    if( Iff && curnode instanceof Iff) {
	        return new NodeReplacement(new And(
	            new Or(new Negate(curnode.getChild(0)), curnode.getChild(1)),
	            new Or(curnode.getChild(0), new Negate(curnode.getChild(1)))));
	    }
	    
	    return null;
	}
}

