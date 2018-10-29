import Employee

class MainEmployee:

    def mainEmp():
        emp=Employee.EmployeeClass('Aigerim', 'Yessenbayeva', 50000)
        # emp_1 = emp.Employee('Aigerim','Yessenbayeva', 50000)
        print(emp.fullname())

    if __name__=='__main__':
        mainEmp()
