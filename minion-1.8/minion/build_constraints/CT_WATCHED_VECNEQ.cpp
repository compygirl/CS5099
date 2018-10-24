#include "../minion.h"
/* Minion Constraint Solver
   http://minion.sourceforge.net
   
   For Licence Information see file LICENSE.txt 
*/

#include "../dynamic_constraints/dynamic_vecneq.h"

template<typename VarArray1,  typename VarArray2>
AbstractConstraint*
BuildCT_WATCHED_VECNEQ(StateObj* stateObj,const VarArray1& varray1, const VarArray2& varray2, ConstraintBlob&)
{ return new ConName <VarArray1,VarArray2>(stateObj, varray1, varray2); }

// these two don't seem to be used anywhere
template<typename VarArray1,  typename VarArray2>
AbstractConstraint*
BuildCT_WATCHED_VEC_OR_LESS(StateObj* stateObj,const VarArray1& varray1, const VarArray2& varray2, ConstraintBlob&)
{ return new ConName <VarArray1,VarArray2, LessIterated>(stateObj, varray1, varray2); }

template<typename VarArray1,  typename VarArray2>
AbstractConstraint*
BuildCT_WATCHED_VEC_OR_AND(StateObj* stateObj,const VarArray1& varray1, const VarArray2& varray2, ConstraintBlob&)
{ return new ConName <VarArray1,VarArray2, BothNonZeroIterated>(stateObj, varray1, varray2); }

BUILD_CT(CT_WATCHED_VECNEQ, 2)
