import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../../../core/theme/app_colors.dart';
import '../bloc/invitation_bloc.dart';
import '../bloc/invitation_event.dart';
import '../bloc/invitation_state.dart';

class ShareListModal extends StatefulWidget {
  final String listId;
  final InvitationBloc invitationBloc;

  const ShareListModal({
    super.key,
    required this.listId,
    required this.invitationBloc,
  });

  @override
  State<ShareListModal> createState() => _ShareListModalState();
}

class _ShareListModalState extends State<ShareListModal> {
  String _selectedRole = 'editor';

  @override
  void initState() {
    super.initState();
    widget.invitationBloc.add(LoadMembersEvent(widget.listId));
    widget.invitationBloc
        .add(CreateInviteEvent(listId: widget.listId, role: _selectedRole));
  }

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<InvitationState>(
      stream: widget.invitationBloc.stream,
      initialData: widget.invitationBloc.state,
      builder: (context, snapshot) {
        final state = snapshot.data ?? const InvitationState();
        final invite = state.currentInvite;

        return Padding(
          padding: EdgeInsets.only(
            bottom: MediaQuery.of(context).viewInsets.bottom + 24,
            top: 24,
            left: 20,
            right: 20,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    'Invite Household Members',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: () => Navigator.pop(context),
                  ),
                ],
              ),
              const SizedBox(height: 6),
              Text(
                'Collaborate in real time. Anyone with this link can view or edit items.',
                style: TextStyle(fontSize: 13, color: Colors.grey.shade600),
              ),
              const SizedBox(height: 16),
              // Role selector
              Row(
                children: [
                  const Text('Member Role: ',
                      style: TextStyle(fontWeight: FontWeight.w600)),
                  const SizedBox(width: 8),
                  DropdownButton<String>(
                    value: _selectedRole,
                    items: const [
                      DropdownMenuItem(
                          value: 'editor',
                          child: Text('Can Edit (Full Access)')),
                      DropdownMenuItem(
                          value: 'viewer', child: Text('Can View (Read-Only)')),
                    ],
                    onChanged: (val) {
                      if (val != null) {
                        setState(() => _selectedRole = val);
                        widget.invitationBloc.add(CreateInviteEvent(
                            listId: widget.listId, role: val));
                      }
                    },
                  ),
                ],
              ),
              const SizedBox(height: 12),
              // Invite link box
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                decoration: BoxDecoration(
                  color: Colors.grey.shade100,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.grey.shade300),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.link, size: 20, color: AppColors.primary),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        invite?.inviteUrl ?? 'Generating dynamic link...',
                        style: const TextStyle(
                            fontSize: 13, fontFamily: 'monospace'),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.copy,
                          size: 20, color: AppColors.primary),
                      tooltip: 'Copy to clipboard',
                      onPressed: invite != null
                          ? () {
                              Clipboard.setData(
                                  ClipboardData(text: invite.inviteUrl));
                              ScaffoldMessenger.of(context).showSnackBar(
                                const SnackBar(
                                    content: Text('Invitation link copied!')),
                              );
                            }
                          : null,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),
              const Text(
                'Current Household Members',
                style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              if (state.members.isEmpty)
                const Text('Loading members...',
                    style: TextStyle(fontSize: 12, color: Colors.grey))
              else
                ...state.members.map((m) => ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: const CircleAvatar(
                        backgroundColor: AppColors.primaryContainer,
                        child: Icon(Icons.person_outline,
                            color: AppColors.primary),
                      ),
                      title: Text(m['name'] ?? ''),
                      trailing: Chip(
                        label: Text(m['role'] ?? '',
                            style: const TextStyle(fontSize: 11)),
                        backgroundColor: Colors.grey.shade100,
                      ),
                    )),
            ],
          ),
        );
      },
    );
  }
}
