#include "../minion.h"
/* Minion Constraint Solver
   http://minion.sourceforge.net
   
   For Licence Information see file LICENSE.txt 
*/

#include "../constraints/sum_constraints/constraint_sum.h"

template<typename VarArray,  typename VarSum>
AbstractConstraint*
BuildCT_LEQSUM(StateObj* stateObj, const VarArray& _var_array, const vector<VarSum>& _var_sum, ConstraintBlob&)
{ 
  if(_var_array.size() == 2)
  {
    std::array<typename VarArray::value_type, 2> v_array;
    for(SysInt i = 0; i < 2; ++i)
      v_array[i] = _var_array[i];
    return LightLessEqualSumCon(stateObj, v_array, _var_sum[0]);
  }
  else
  {
    return new LessEqualSumConstraint<VarArray, VarSum>(stateObj, _var_array, _var_sum[0]); 
  }
}

inline AbstractConstraint*
BuildCT_LEQSUM(StateObj* stateObj, const vector<BoolVarRef>& var_array, const vector<ConstantVar>& var_sum, ConstraintBlob&)
{ 
  SysInt t2(checked_cast<SysInt>(var_sum[0].getAssignedValue()));
  return BoolLessEqualSumCon(stateObj, var_array, t2);
}

BUILD_CT(CT_LEQSUM, 2)
