package com.hannes.familytree.members

import androidx.compose.runtime.Composable
import com.hannes.familytree.gedcom.FilePickerLauncher

interface MemberStorage {
    fun read(): String?
    fun write(content: String)
}

fun MemberStorage.loadMembers(): List<Member> =
    read()?.let(MemberData::decode) ?: MemberData.initialMembers()

fun MemberStorage.saveMember(members: List<Member>, member: Member): List<Member> {
    val updated = MemberData.update(members, member)
    write(MemberData.encode(updated))
    return updated
}

@Composable
expect fun rememberMemberStorage(): MemberStorage

@Composable
expect fun rememberMemberImagePicker(onPicked: (Result<MemberImage>?) -> Unit): FilePickerLauncher

@Composable
expect fun MemberEditorBackHandler(onBack: () -> Unit)
