<template>
  <el-form
    ref="studentForm"
    :model="studentBeingEdited"
    :rules="rules"
    label-width="110px"
    style="padding-right: 30px"
    label-position="left"
  >
    <el-form-item label="Id:">
      <el-input v-model="studentBeingEdited.id" disabled></el-input>
    </el-form-item>
    <el-form-item label="Username:" prop="username">
      <el-input v-model="studentBeingEdited.username"></el-input>
    </el-form-item>
    <el-form-item label="Status:" prop="enabled">
      <!-- UC-STU-deactivate-student: an active student is deactivated only through the confirmed action, never by
           saving the form. A deactivated one keeps the switch, which can only re-enable her
           (TD-student-reactivation-stopgap, until UC-STU-reactivate-student is built). -->
      <template v-if="activeWhenLoaded">
        <el-tag type="success">Active</el-tag>
        <el-button
          type="danger"
          plain
          style="margin-left: 12px"
          data-cy="deactivate-student"
          @click="confirmDeactivation()"
        >
          Deactivate student
        </el-button>
      </template>
      <el-switch
        v-else
        v-model="studentBeingEdited.enabled"
        inline-prompt
        active-text="Active"
        inactive-text="Deactivated"
        style="--el-switch-on-color: #13ce66; --el-switch-off-color: #ff4949"
      />
    </el-form-item>
    <el-form-item label="First Name:" prop="firstName">
      <el-input v-model="studentBeingEdited.firstName"></el-input>
    </el-form-item>
    <el-form-item label="Last Name:" prop="lastName">
      <el-input v-model="studentBeingEdited.lastName"></el-input>
    </el-form-item>
    <el-form-item label="Email:" prop="email">
      <el-input v-model="studentBeingEdited.email"></el-input>
    </el-form-item>
    <el-form-item label="Team:" prop="teamName">
      <el-input v-model="studentBeingEdited.teamName" disabled></el-input>
    </el-form-item>
    <div style="display: flex; justify-content: end">
      <el-button @click="$emit('close-dialog')"> Cancel </el-button>
      <el-button type="primary" @click="updateExistingStudent()"> Confirm </el-button>
    </div>
  </el-form>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Student } from '@/apis/student/types'
import type { FormInstance } from 'element-plus'
import { deactivateStudent, findStudentById, updateStudent } from '@/apis/student'
import { h, ref, onMounted } from 'vue'

const { studentId } = defineProps(['studentId'])
const emit = defineEmits(['close-dialog'])

// Note that the structure of the userData is NOT the same as the User interface since the roles are stored as an array
const studentBeingEdited = ref<Student>({
  // Data of the user being added or edited
  id: NaN,
  username: '',
  enabled: false,
  firstName: '',
  lastName: '',
  email: ''
})

const studentForm = ref<FormInstance>() // Form reference

const activeWhenLoaded = ref(false) // Whether the student was active when the dialog opened

// Validation rules
const rules = {
  username: [
    { required: true, message: 'Please input your email as your username', trigger: 'blur' },
    {
      pattern: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
      message: 'Enter your email as username.',
      trigger: 'blur'
    }
  ],
  enabled: [{ required: true, message: 'Please select the status of the user.', trigger: 'blur' }],
  firstName: [
    { required: true, message: 'Please provide the first name of the user.', trigger: 'blur' }
  ],
  lastName: [
    { required: true, message: 'Please provide the last name of the user.', trigger: 'blur' }
  ],
  email: [
    { required: true, message: 'Please provide the email of the user.', trigger: 'blur' },
    { type: 'email', message: 'Please provide a valid email.', trigger: 'blur' }
  ]
}

async function loadStudent(studentId: number) {
  // Load the user to be edited
  const result = await findStudentById(studentId)

  studentBeingEdited.value.id = result.data.id as number
  studentBeingEdited.value.username = result.data.username
  studentBeingEdited.value.enabled = result.data.enabled
  studentBeingEdited.value.firstName = result.data.firstName
  studentBeingEdited.value.lastName = result.data.lastName
  studentBeingEdited.value.email = result.data.email
  studentBeingEdited.value.teamName = result.data.teamName
  activeWhenLoaded.value = result.data.enabled === true
}

// The consequences of a deactivation, from UC-STU-deactivate-student's Associated Information (step 4)
const deactivationConsequences = [
  'The student can no longer log in, and a session already open ends on its next request.',
  'The student stays on the team, and their submitted weekly activity reports and peer evaluations are kept.',
  'The student no longer owes weekly activity reports or peer evaluations, and no teammate owes a peer evaluation of them, so they drop out of reminders.',
  'Teammates can no longer evaluate the student.',
  'The student can be reactivated later. This is not a deletion, which is permanent.'
]

async function confirmDeactivation() {
  const name = `${studentBeingEdited.value.firstName} ${studentBeingEdited.value.lastName}`
  try {
    await ElMessageBox.confirm(
      h('div', { 'data-cy': 'deactivate-consequences' }, [
        h('p', `Deactivate ${name}?`),
        h(
          'ul',
          { style: 'padding-left: 18px; margin: 8px 0 0' },
          deactivationConsequences.map((consequence) => h('li', consequence))
        )
      ]),
      'Deactivate student',
      {
        type: 'warning',
        confirmButtonText: 'Confirm',
        cancelButtonText: 'Cancel',
        customClass: 'deactivate-student-confirm'
      }
    )
  } catch {
    return // 5a: cancelled, nothing is sent
  }

  try {
    await deactivateStudent(studentBeingEdited.value.id as number)
  } catch {
    return // 6a and others: request.ts has already shown the message; a 403 (6b) routes to the forbidden page
  }
  ElMessage.success(`${name} has been deactivated.`) // Step 6
  emit('close-dialog') // Students.vue reloads its current page
}

onMounted(() => {
  loadStudent(studentId) // Load the artifact to be edited
})

async function updateExistingStudent() {
  await studentForm.value!.validate() // Validate the form

  const updatedStudent: Student = {
    id: studentBeingEdited.value.id,
    username: studentBeingEdited.value.username,
    enabled: studentBeingEdited.value.enabled,
    firstName: studentBeingEdited.value.firstName,
    lastName: studentBeingEdited.value.lastName,
    email: studentBeingEdited.value.email
  }
  console.log(updatedStudent)
  // Call the API to update the user
  await updateStudent(updatedStudent)

  ElMessage.success('Student updated successfully')
  emit('close-dialog')
}
</script>

<style scoped lang="scss"></style>
