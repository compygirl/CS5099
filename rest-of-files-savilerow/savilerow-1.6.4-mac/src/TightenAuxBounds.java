package savilerow;
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
import savilerow.treetransformer.*;
import savilerow.model.*;
import savilerow.solver.*;

import java.util.* ;
import java.io.* ;

// After flattening, improve the aux variables by tightening their bounds

public class TightenAuxBounds 
{
    public void TightenBounds(Model m) {
        // Get the variable list
        HashMap<String, categoryentry> cats=m.global_symbols.getCategories();
        for(Map.Entry<String,categoryentry> a : cats.entrySet()) {
            if(a.getValue().cat==ASTNode.Auxiliary) {
                ASTNode d=m.global_symbols.getDomain(a.getKey());
                ArrayList<Long> values=d.getValueSet();
                
                long lowerbound=values.get(0);
                long upperbound=values.get(values.size()-1);
                TransformSimplify ts=new TransformSimplify();
                while(lowerbound<=upperbound) {
                    // Sub val in everywhere that the identifier occurs
                    ReplaceASTNode r=new ReplaceASTNode(new Identifier(a.getKey(), m.global_symbols), new NumberConstant(lowerbound));
                    ASTNode temp=r.transform(m.constraints.copy());
                    temp=ts.transform(temp);
                    if(temp.equals(new BooleanConstant(false))) {
                        lowerbound++;
                    }
                    else {
                        break;
                    }
                }
                
                while(upperbound>=lowerbound) {
                    // Sub val in everywhere that the identifier occurs
                    ReplaceASTNode r=new ReplaceASTNode(new Identifier(a.getKey(), m.global_symbols), new NumberConstant(upperbound));
                    ASTNode temp=r.transform(m.constraints.copy());
                    temp=ts.transform(temp);
                    if(temp.equals(new BooleanConstant(false))) {
                        upperbound--;
                    }
                    else {
                        break;
                    }
                }
                
                d=new IntegerDomain(new Range(new NumberConstant(lowerbound), new NumberConstant(upperbound)));
                m.global_symbols.setDomain(a.getKey(), d);
            }
        }
    }
}


