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

// For Dominion output, take in-place constant matrices out of constraints and
// make them lettings. 

public class TransformConstMatrixClass extends TreeTransformerBottomUpNoWrapper
{
    int matcounter;
    public TransformConstMatrixClass(Model _m) {
        super(_m);
        matcounter=0;
    }
    
    protected NodeReplacement processNode(ASTNode curnode)
	{
	    if(curnode instanceof CompoundMatrix && curnode.getCategory()==ASTNode.Constant && 
	        !(curnode.getParent() instanceof CompoundMatrix) && isConstantMatrix(curnode) )
        {
            System.out.println("Pulling out constant matrix: "+curnode);
            
            matcounter++;
            
            m.global_symbols.newConstantMatrix("_MATRIX_ujbstj_"+matcounter, (CompoundMatrix) curnode.copy());
            return new NodeReplacement(new Identifier("_MATRIX_ujbstj_"+matcounter, m.global_symbols));
        }
        return null;
    }
    
    private boolean isConstantMatrix(ASTNode a) {
        if(a instanceof CompoundMatrix) {
            for(ASTNode b: a.getChildren()) {
                if(!isConstantMatrix(b)) return false;
            }
            return true;
        }
        else {
            return a instanceof NumberConstant || a instanceof BooleanConstant;
        }
    }
}

