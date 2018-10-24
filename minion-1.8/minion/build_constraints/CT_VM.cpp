#include "../minion.h"
/* Minion Constraint Solver
   http://minion.sourceforge.net
   
   For Licence Information see file LICENSE.txt 
*/

#include "../constraints/vm.h"

template <typename T>
AbstractConstraint*
BuildCT_VM(StateObj* stateObj,const T& t1, ConstraintBlob& b)
{ return VMCon(stateObj, t1, b.tuples, b.tuples2); }

BUILD_CT(CT_VM, 1)
