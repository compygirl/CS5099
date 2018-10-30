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

import savilerow.*;
import savilerow.expression.*;
import savilerow.model.*;

import java.util.*;

// Very simple decomposition of lex constraints -- would benefit from AC-CSE to
// factor out the conjunctions of disequality constraints.

public class TransformDecomposeLex extends TreeTransformerBottomUpNoWrapper
{
    public TransformDecomposeLex(Model _m) { super(_m); }
    
    protected NodeReplacement processNode(ASTNode curnode)
	{
	    // Decompose Lex constraints for SAT output. 
        if(curnode instanceof LexLess || curnode instanceof LexLessEqual) {
            ASTNode matrix1 = curnode.getChild(0);
            ASTNode matrix2 = curnode.getChild(1);
            
            assert matrix1 instanceof CompoundMatrix && matrix2 instanceof CompoundMatrix;
            
            // Assume the matrix is more than one element long. Otherwise would have been simplified away. 
            ArrayList<ASTNode> newcts=new ArrayList<ASTNode>();
            
            for (int i=1; i < matrix1.numChildren(); i++) {
                
                ArrayList<ASTNode> disj=new ArrayList<ASTNode>();
                
                for(int j=1; j<i; j++) {
                    disj.add(new AllDifferent(matrix1.getChild(j), matrix2.getChild(j)));
                }
                
                if(i<matrix1.numChildren()-1 || curnode instanceof LexLessEqual) {
                    disj.add(new LessEqual(matrix1.getChild(i), matrix2.getChild(i)));
                }
                else {
                    disj.add(new Less(matrix1.getChild(i), matrix2.getChild(i))); // Final case for LexLess only. 
                }
                
                newcts.add(new Or(disj));
            }
            
            return new NodeReplacement(new And(newcts));
        }
        return null;
    }
    
}

