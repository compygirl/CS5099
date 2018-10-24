#include "../minion.h"
/* Minion Constraint Solver
   http://minion.sourceforge.net
   
   For Licence Information see file LICENSE.txt 
*/

#include "../dynamic_constraints/dynamic_element.h"

template<typename Var1, typename Var2>
AbstractConstraint*
BuildCT_WATCHED_ELEMENT_UNDEFZERO(StateObj* stateObj, const Var1& vararray, const Var2& v1, const Var1& v2, ConstraintBlob&)
{ 
  return new ElementConstraintDynamic<Var1, typename Var2::value_type, typename Var1::value_type, true>
              (stateObj, vararray, v1[0], v2[0]);  
}

template<typename Var1, typename Var2, typename Var3>
AbstractConstraint*
BuildCT_WATCHED_ELEMENT_UNDEFZERO(StateObj* stateObj, Var1 vararray, const Var2& v1, const Var3& v2, ConstraintBlob&)
{ 
  return new ElementConstraintDynamic<Var1, typename Var2::value_type, AnyVarRef, true>
              (stateObj, vararray, v1[0], AnyVarRef(v2[0]));  
}

BUILD_CT(CT_WATCHED_ELEMENT_UNDEFZERO, 3)
